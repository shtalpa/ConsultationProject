package com.campus.mcp.server.tools;

import com.campus.mcp.server.kb.DataStore;
import com.campus.mcp.server.kb.KnowledgeBase;
import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import io.modelcontextprotocol.spec.McpSchema.Tool;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Builds the campus {@code Tools} exposed to MCP clients. Tools are actions the LLM can choose
 * to invoke: searching the knowledge base (RAG retrieval), checking availability, making a
 * booking, listing lecturer slots, and submitting a leave application.
 */
public final class CampusTools {

    private final KnowledgeBase kb;
    private final DataStore dataStore;
    private final McpJsonMapper jsonMapper;

    public CampusTools(KnowledgeBase kb, DataStore dataStore, McpJsonMapper jsonMapper) {
        this.kb = kb;
        this.dataStore = dataStore;
        this.jsonMapper = jsonMapper;
    }

    public List<SyncToolSpecification> all() {
        return List.of(
                searchCampusInfo(),
                checkRoomAvailability(),
                bookResource(),
                listLecturerSlots(),
                submitLeaveApplication());
    }

    // 1. RAG retrieval tool -------------------------------------------------

    private SyncToolSpecification searchCampusInfo() {
        String schema = """
            {
              "type": "object",
              "properties": {
                "query": { "type": "string", "description": "Natural-language question about campus services" },
                "topK":  { "type": "integer", "description": "How many passages to return (default 3)" }
              },
              "required": ["query"]
            }
            """;
        return SyncToolSpecification.builder()
                .tool(Tool.builder()
                        .name("search_campus_info")
                        .description("Retrieve the most relevant passages from the campus knowledge base "
                                + "for a question. Use this to ground answers (the Retrieval step of RAG).")
                        .inputSchema(jsonMapper, schema)
                        .build())
                .callHandler((exchange, request) -> {
                    Map<String, Object> a = request.arguments();
                    String query = str(a, "query");
                    int topK = a.get("topK") instanceof Number n ? n.intValue() : 3;
                    List<KnowledgeBase.Hit> hits = kb.retrieve(query, topK);
                    if (hits.isEmpty()) {
                        return text("No relevant passages found for: " + query);
                    }
                    String body = hits.stream()
                            .map(h -> "Source: " + h.source() + "\n" + h.text())
                            .collect(Collectors.joining("\n\n---\n\n"));
                    return text(body);
                })
                .build();
    }

    // 2. Room availability --------------------------------------------------

    private SyncToolSpecification checkRoomAvailability() {
        String schema = """
            {
              "type": "object",
              "properties": {
                "date":      { "type": "string", "description": "Date in yyyy-MM-dd" },
                "building":  { "type": "string", "description": "Optional building code, e.g. D, E, LIB, OUT" },
                "startTime": { "type": "string", "description": "Optional start time HH:mm. With endTime, a room counts as booked only if an existing booking overlaps this window." },
                "endTime":   { "type": "string", "description": "Optional end time HH:mm." }
              },
              "required": ["date"]
            }
            """;
        return SyncToolSpecification.builder()
                .tool(Tool.builder()
                        .name("check_room_availability")
                        .description("List bookable rooms and which ones already have bookings on a given date.")
                        .inputSchema(jsonMapper, schema)
                        .build())
                .callHandler((exchange, request) -> {
                    Map<String, Object> a = request.arguments();
                    String date = str(a, "date");
                    String building = str(a, "building");
                    String startTime = str(a, "startTime");
                    String endTime = str(a, "endTime");

                    // Time filtering only applies when BOTH times are supplied and valid.
                    boolean timeFilter = !startTime.isBlank() && !endTime.isBlank();
                    LocalTime reqStart = null;
                    LocalTime reqEnd = null;
                    if (timeFilter) {
                        try {
                            reqStart = LocalTime.parse(startTime.strip());
                            reqEnd = LocalTime.parse(endTime.strip());
                        } catch (Exception e) {
                            return error("Invalid time format. Use HH:mm for startTime and endTime.");
                        }
                        if (!reqEnd.isAfter(reqStart)) {
                            return error("endTime must be later than startTime.");
                        }
                    }

                    List<String[]> rooms = parseRooms();

                    // Keep the full booking rows (with their time windows) for this date.
                    List<String[]> dayBookings = dataStore.bookingsOn(date).stream()
                            .map(line -> line.split("\\s*\\|\\s*"))
                            .filter(p -> p.length >= 2)
                            .collect(Collectors.toList());

                    StringBuilder sb = new StringBuilder(timeFilter
                            ? "Availability on " + date + " (" + startTime + "-" + endTime + "):\n"
                            : "Availability on " + date + ":\n");

                    for (String[] r : rooms) {
                        String id = r[0], type = r[1], cap = r[2], bldg = r[3];
                        if (!building.isBlank() && !bldg.equalsIgnoreCase(building)) {
                            continue;
                        }

                        boolean isBooked;
                        if (!timeFilter) {
                            // Per-day view (original behaviour): booked if any booking exists.
                            isBooked = dayBookings.stream().anyMatch(p -> p[1].equalsIgnoreCase(id));
                        } else {
                            // Time-aware: booked only if a booking overlaps the requested window.
                            final LocalTime rs = reqStart, re = reqEnd;
                            isBooked = dayBookings.stream()
                                    .filter(p -> p[1].equalsIgnoreCase(id))
                                    .anyMatch(p -> overlaps(p, rs, re));
                        }

                        sb.append(String.format("  %-7s %-16s cap %-3s %s  -> %s%n",
                                id, type, cap, bldg, isBooked ? "BOOKED" : "free"));
                    }
                    return text(sb.toString());
                })
                .build();
    }

    // 3. Book a resource ----------------------------------------------------

    private SyncToolSpecification bookResource() {
        String schema = """
            {
              "type": "object",
              "properties": {
                "resourceId": { "type": "string", "description": "Room/resource id, e.g. KA-P1" },
                "date":       { "type": "string", "description": "Date in yyyy-MM-dd" },
                "startTime":  { "type": "string", "description": "Start time HH:mm" },
                "endTime":    { "type": "string", "description": "End time HH:mm" },
                "studentId":  { "type": "string", "description": "Student id of the requester" }
              },
              "required": ["resourceId", "date", "startTime", "endTime", "studentId"]
            }
            """;
        return SyncToolSpecification.builder()
                .tool(Tool.builder()
                        .name("book_resource")
                        .description("Create a booking for a bookable campus resource. Returns a booking reference.")
                        .inputSchema(jsonMapper, schema)
                        .build())
                .callHandler((exchange, request) -> {
                    Map<String, Object> a = request.arguments();
                    String resourceId = str(a, "resourceId");
                    boolean known = parseRooms().stream().anyMatch(r -> r[0].equalsIgnoreCase(resourceId));
                    if (!known) {
                        return error("Unknown resource '" + resourceId + "'. Use check_room_availability first.");
                    }
                    String ref = dataStore.addBooking(resourceId, str(a, "date"),
                            str(a, "startTime"), str(a, "endTime"), str(a, "studentId"));
                    return text("Booking confirmed. Reference " + ref + " for " + resourceId
                            + " on " + str(a, "date") + " " + str(a, "startTime") + "-" + str(a, "endTime") + ".");
                })
                .build();
    }

    // 4. Lecturer slots -----------------------------------------------------

    private SyncToolSpecification listLecturerSlots() {
        String schema = """
            {
              "type": "object",
              "properties": {
                "lecturerName": { "type": "string", "description": "Full or partial lecturer name" },
                "day":          { "type": "string", "description": "Optional weekday, e.g. Monday" }
              },
              "required": ["lecturerName"]
            }
            """;
        return SyncToolSpecification.builder()
                .tool(Tool.builder()
                        .name("list_lecturer_slots")
                        .description("List published consultation slots for a lecturer, optionally filtered by weekday.")
                        .inputSchema(jsonMapper, schema)
                        .build())
                .callHandler((exchange, request) -> {
                    Map<String, Object> a = request.arguments();
                    String name = str(a, "lecturerName").toLowerCase();
                    String day = str(a, "day").toLowerCase();
                    List<String> matches = new ArrayList<>();
                    for (String line : kb.rawDocument("lecturers.txt").split("\\r?\\n")) {
                        if (line.isBlank() || line.startsWith("#") || line.startsWith(":=")) {
                            continue;
                        }
                        String[] p = line.split("\\s*\\|\\s*");
                        if (p.length < 4) {
                            continue;
                        }
                        boolean nameOk = p[0].toLowerCase().contains(name);
                        boolean dayOk = day.isBlank() || p[2].toLowerCase().contains(day);
                        if (nameOk && dayOk) {
                            matches.add(p[0].strip() + " (" + p[1].strip() + ") " + p[2].strip()
                                    + ": " + p[3].strip());
                        }
                    }
                    return matches.isEmpty()
                            ? text("No consultation slots found for '" + str(a, "lecturerName") + "'.")
                            : text(String.join("\n", matches));
                })
                .build();
    }

    // 5. Leave application --------------------------------------------------

    private SyncToolSpecification submitLeaveApplication() {
        String schema = """
            {
              "type": "object",
              "properties": {
                "studentId": { "type": "string" },
                "fromDate":  { "type": "string", "description": "yyyy-MM-dd" },
                "toDate":    { "type": "string", "description": "yyyy-MM-dd" },
                "reason":    { "type": "string" }
              },
              "required": ["studentId", "fromDate", "toDate", "reason"]
            }
            """;
        return SyncToolSpecification.builder()
                .tool(Tool.builder()
                        .name("submit_leave_application")
                        .description("Submit a student leave application. Returns a leave reference number.")
                        .inputSchema(jsonMapper, schema)
                        .build())
                .callHandler((exchange, request) -> {
                    Map<String, Object> a = request.arguments();
                    String ref = dataStore.addLeave(str(a, "studentId"), str(a, "fromDate"),
                            str(a, "toDate"), str(a, "reason"));
                    return text("Leave application received. Reference " + ref
                            + ". The programme office will review it within 2 working days.");
                })
                .build();
    }

    // ---- helpers ----------------------------------------------------------

    /**
     * True if a booking row's time window overlaps the requested [reqStart, reqEnd) window.
     * Bookings with blank or unparseable times are treated as all-day (always conflict), so a
     * timeless booking still blocks the room.
     */
    private static boolean overlaps(String[] booking, LocalTime reqStart, LocalTime reqEnd) {
        if (booking.length < 5 || booking[3].isBlank() || booking[4].isBlank()) {
            return true; // no time recorded -> treat as occupying the whole day
        }
        try {
            LocalTime bStart = LocalTime.parse(booking[3].strip());
            LocalTime bEnd = LocalTime.parse(booking[4].strip());
            // Half-open overlap: two windows overlap iff each starts before the other ends.
            return bStart.isBefore(reqEnd) && reqStart.isBefore(bEnd);
        } catch (Exception e) {
            return true; // unparseable time -> be conservative and count it as booked
        }
    }

    /** Parses the room table from facilities.txt into [id, type, capacity, building] rows. */
    private List<String[]> parseRooms() {
        List<String[]> rooms = new ArrayList<>();
        for (String line : kb.rawDocument("facilities.txt").split("\\r?\\n")) {
            String[] p = Arrays.stream(line.split("\\s*\\|\\s*")).map(String::strip).toArray(String[]::new);

            //  UPDATED REGEX: Allows letters, numbers, hyphens, and dots in the room code
            if (p.length >= 4 && p[0].matches("[A-Z0-9\\.-]+") && p[2].matches("\\d+")) {
                rooms.add(new String[]{p[0], p[1], p[2], p[3]});
            }
        }
        return rooms;
    }

    private static CallToolResult text(String message) {
        return CallToolResult.builder().content(List.of(new TextContent(message))).build();
    }

    private static CallToolResult error(String message) {
        return CallToolResult.builder().content(List.of(new TextContent(message))).isError(true).build();
    }

    private static String str(Map<String, Object> args, String key) {
        Object v = args.get(key);
        return v == null ? "" : v.toString();
    }
}

package ru.practicum.moviehub.http;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.sun.net.httpserver.HttpExchange;
import ru.practicum.moviehub.api.ErrorResponse;
import ru.practicum.moviehub.model.Movie;
import ru.practicum.moviehub.store.MoviesStore;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Year;
import java.util.ArrayList;
import java.util.List;

public class MoviesHandler extends BaseHttpHandler {

    private static final Gson gson = new Gson();
    private final MoviesStore store;

    public MoviesHandler(MoviesStore store) {
        this.store = store;
    }

    @Override
    protected void handleInternal(HttpExchange ex) throws IOException {
        String method = ex.getRequestMethod();

        switch (method) {
            case "GET" -> handleGet(ex);
            case "POST" -> handlePost(ex);
            case "DELETE" -> handleDelete(ex);
            default -> ex.sendResponseHeaders(405, -1);
        }
    }

    // ---------- GET ----------

    private void handleGet(HttpExchange ex) throws IOException {
        String path = ex.getRequestURI().getPath();
        String query = ex.getRequestURI().getQuery();

        if (path.equals("/movies")) {
            if (query == null) {
                sendJson(ex, 200, gson.toJson(store.findAll()));
                return;
            }

            Integer year = extractYear(query);
            if (year == null) {
                ex.sendResponseHeaders(400, -1);
                return;
            }

            sendJson(ex, 200, gson.toJson(store.findByYear(year)));
            return;
        }

        if (path.startsWith("/movies/")) {
            try {
                long id = Long.parseLong(path.substring(8));
                var movie = store.findById(id);

                if (movie.isEmpty()) {
                    ex.sendResponseHeaders(404, -1);
                    return;
                }

                sendJson(ex, 200, gson.toJson(movie.get()));
            } catch (NumberFormatException e) {
                ex.sendResponseHeaders(400, -1);
            }
            return;
        }

        ex.sendResponseHeaders(404, -1);
    }

    // ---------- POST ----------

    private void handlePost(HttpExchange ex) throws IOException {

        String contentType = ex.getRequestHeaders().getFirst("Content-Type");
        if (contentType == null || !contentType.toLowerCase().startsWith("application/json")) {
            ex.sendResponseHeaders(415, -1);
            return;
        }

        String body;
        try (InputStream is = ex.getRequestBody()) {
            body = new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }

        Movie incoming;
        try {
            incoming = gson.fromJson(body, Movie.class);
        } catch (JsonSyntaxException e) {
            ex.sendResponseHeaders(422, -1);
            return;
        }

        List<String> errors = new ArrayList<>();

        String title = incoming.getTitle();
        if (title == null || title.isBlank() || title.length() > 100) {
            errors.add("title");
        }

        int maxYear = Year.now().getValue() + 1;
        if (incoming.getYear() < 1888 || incoming.getYear() > maxYear) {
            errors.add("year");
        }

        if (!errors.isEmpty()) {
            sendJson(ex, 422, gson.toJson(
                    new ErrorResponse("Ошибка валидации", errors)
            ));
            return;
        }

        Movie saved = store.add(incoming.getTitle(), incoming.getYear());
        sendJson(ex, 201, gson.toJson(saved));
    }

    // ---------- DELETE ----------

    private void handleDelete(HttpExchange ex) throws IOException {
        String path = ex.getRequestURI().getPath();

        if (!path.startsWith("/movies/")) {
            ex.sendResponseHeaders(404, -1);
            return;
        }

        try {
            long id = Long.parseLong(path.substring(8));
            boolean removed = store.delete(id);

            if (!removed) {
                ex.sendResponseHeaders(404, -1);
                return;
            }

            ex.sendResponseHeaders(204, -1);
        } catch (NumberFormatException e) {
            ex.sendResponseHeaders(400, -1);
        }
    }

    private Integer extractYear(String query) {
        for (String param : query.split("&")) {
            if (param.startsWith("year=")) {
                try {
                    return Integer.parseInt(param.substring(5));
                } catch (NumberFormatException e) {
                    return null;
                }
            }
        }
        return null;
    }
}
package ru.practicum.moviehub.http;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.sun.net.httpserver.HttpExchange;
import ru.practicum.moviehub.api.ErrorResponse;
import ru.practicum.moviehub.api.HttpStatus;
import ru.practicum.moviehub.model.Movie;
import ru.practicum.moviehub.store.MoviesStore;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Year;
import java.util.ArrayList;
import java.util.List;

public class MoviesHandler extends BaseHttpHandler {

    private final Gson gson = new Gson();
    private final MoviesStore store;

    public MoviesHandler(MoviesStore store) {
        this.store = store;
    }

    @Override
    protected void handleInternal(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod();

        switch (method) {
            case "GET" -> handleGet(exchange);
            case "POST" -> handlePost(exchange);
            case "DELETE" -> handleDelete(exchange);
            default -> exchange.sendResponseHeaders(
                    HttpStatus.METHOD_NOT_ALLOWED.getCode(), -1
            );
        }
    }

    // ---------- GET ----------

    private void handleGet(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        String query = exchange.getRequestURI().getQuery();

        if ("/movies".equals(path)) {

            if (query == null) {
                sendJson(exchange, HttpStatus.OK, gson.toJson(store.findAll()));
                return;
            }

            Integer year = extractYear(query);
            if (year == null) {
                exchange.sendResponseHeaders(
                        HttpStatus.BAD_REQUEST.getCode(), -1
                );
                return;
            }

            sendJson(
                    exchange,
                    HttpStatus.OK,
                    gson.toJson(store.findByYear(year))
            );
            return;
        }

        if (path.startsWith("/movies/")) {
            try {
                long id = Long.parseLong(path.substring("/movies/".length()));
                var movie = store.findById(id);

                if (movie.isEmpty()) {
                    exchange.sendResponseHeaders(
                            HttpStatus.NOT_FOUND.getCode(), -1
                    );
                    return;
                }

                sendJson(
                        exchange,
                        HttpStatus.OK,
                        gson.toJson(movie.get())
                );
            } catch (NumberFormatException e) {
                exchange.sendResponseHeaders(
                        HttpStatus.BAD_REQUEST.getCode(), -1
                );
            }
            return;
        }

        exchange.sendResponseHeaders(
                HttpStatus.NOT_FOUND.getCode(), -1
        );
    }

    // ---------- POST ----------

    private void handlePost(HttpExchange exchange) throws IOException {

        String contentType = exchange.getRequestHeaders().getFirst("Content-Type");
        if (contentType == null || !contentType.toLowerCase().startsWith("application/json")) {
            exchange.sendResponseHeaders(
                    HttpStatus.UNSUPPORTED_MEDIA_TYPE.getCode(), -1
            );
            return;
        }

        String body;
        try (InputStream is = exchange.getRequestBody()) {
            body = new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }

        Movie incoming;
        try {
            incoming = gson.fromJson(body, Movie.class);
        } catch (JsonSyntaxException e) {
            exchange.sendResponseHeaders(
                    HttpStatus.UNPROCESSABLE_ENTITY.getCode(), -1
            );
            return;
        }

        List<String> errors = validateMovie(incoming);

        if (!errors.isEmpty()) {
            sendJson(
                    exchange,
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    gson.toJson(new ErrorResponse("Ошибка валидации", errors))
            );
            return;
        }

        Movie saved = store.add(incoming.getTitle(), incoming.getYear());
        sendJson(
                exchange,
                HttpStatus.CREATED,
                gson.toJson(saved)
        );
    }

    // ---------- DELETE ----------

    private void handleDelete(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();

        if (!path.startsWith("/movies/")) {
            exchange.sendResponseHeaders(
                    HttpStatus.NOT_FOUND.getCode(), -1
            );
            return;
        }

        try {
            long id = Long.parseLong(path.substring("/movies/".length()));
            boolean removed = store.delete(id);

            if (!removed) {
                exchange.sendResponseHeaders(
                        HttpStatus.NOT_FOUND.getCode(), -1
                );
                return;
            }

            exchange.sendResponseHeaders(
                    HttpStatus.NO_CONTENT.getCode(), -1
            );
        } catch (NumberFormatException e) {
            exchange.sendResponseHeaders(
                    HttpStatus.BAD_REQUEST.getCode(), -1
            );
        }
    }

    // ---------- VALIDATION ----------

    private List<String> validateMovie(Movie movie) {
        List<String> errors = new ArrayList<>();

        String title = movie.getTitle();
        if (title == null || title.isBlank() || title.length() > 100) {
            errors.add("title");
        }

        int maxYear = Year.now().getValue() + 1;
        if (movie.getYear() < 1888 || movie.getYear() > maxYear) {
            errors.add("year");
        }

        return errors;
    }

    private Integer extractYear(String query) {
        for (String param : query.split("&")) {
            if (param.startsWith("year=")) {
                try {
                    return Integer.parseInt(param.substring("year=".length()));
                } catch (NumberFormatException e) {
                    return null;
                }
            }
        }
        return null;
    }
}

package ru.practicum.moviehub.http;

import com.google.gson.Gson;
import org.junit.jupiter.api.*;
import ru.practicum.moviehub.model.Movie;
import ru.practicum.moviehub.store.MoviesStore;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class MoviesApiTest {

    private static MoviesServer server;
    private static HttpClient client;
    private static final Gson gson = new Gson();
    private static final String BASE_URL = "http://localhost:8080";

    @BeforeAll
    static void beforeAll() {
        server = new MoviesServer(new MoviesStore(), 8080);
        server.start();
        client = HttpClient.newHttpClient();
    }

    @AfterAll
    static void afterAll() {
        server.stop();
    }

    // ---------- POST /movies ----------

    @Test
    void postMovies_addsMovie_whenValid() throws Exception {
        String body = "{ \"title\": \"Inception\", \"year\": 2010 }";

        HttpResponse<String> response = sendPost("/movies", body);

        Movie movie = gson.fromJson(response.body(), Movie.class);

        assertEquals(201, response.statusCode());
        assertNotNull(movie.getId());
        assertEquals("Inception", movie.getTitle());
    }

    @Test
    void postMovies_returns422_whenTitleEmpty() throws Exception {
        String body = "{ \"title\": \"\", \"year\": 2000 }";

        HttpResponse<String> response = sendPost("/movies", body);

        assertEquals(422, response.statusCode());
    }

    @Test
    void postMovies_returns422_whenTitleTooLong() throws Exception {
        String longTitle = "a".repeat(101);
        String body = "{ \"title\": \"" + longTitle + "\", \"year\": 2000 }";

        HttpResponse<String> response = sendPost("/movies", body);

        assertEquals(422, response.statusCode());
    }

    @Test
    void postMovies_returns422_whenYearInvalid() throws Exception {
        String body = "{ \"title\": \"Test\", \"year\": 1800 }";

        HttpResponse<String> response = sendPost("/movies", body);

        assertEquals(422, response.statusCode());
    }

    @Test
    void postMovies_returns415_whenWrongContentType() throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/movies"))
                .header("Content-Type", "text/plain")
                .POST(HttpRequest.BodyPublishers.ofString("test"))
                .build();

        HttpResponse<String> response =
                client.send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(415, response.statusCode());
    }

    @Test
    void postMovies_returns422_whenInvalidJson() throws Exception {
        String body = "{ title: }";

        HttpResponse<String> response = sendPost("/movies", body);

        assertEquals(422, response.statusCode());
    }

    // ---------- GET /movies/{id} ----------

    @Test
    void getMovieById_returnsMovie_whenExists() throws Exception {
        Movie movie = addMovie("Avatar", 2009);

        HttpResponse<String> response = sendGet("/movies/" + movie.getId());
        Movie result = gson.fromJson(response.body(), Movie.class);

        assertEquals(200, response.statusCode());
        assertEquals(movie.getId(), result.getId());
    }

    @Test
    void getMovieById_returns404_whenNotFound() throws Exception {
        HttpResponse<String> response = sendGet("/movies/9999");

        assertEquals(404, response.statusCode());
    }

    @Test
    void getMovieById_returns400_whenIdNotNumber() throws Exception {
        HttpResponse<String> response = sendGet("/movies/abc");

        assertEquals(400, response.statusCode());
    }

    // ---------- DELETE /movies/{id} ----------

    @Test
    void deleteMovie_removesMovie_whenExists() throws Exception {
        Movie movie = addMovie("Titanic", 1997);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/movies/" + movie.getId()))
                .DELETE()
                .build();

        HttpResponse<String> response =
                client.send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(204, response.statusCode());
    }

    @Test
    void deleteMovie_returns404_whenNotFound() throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/movies/9999"))
                .DELETE()
                .build();

        HttpResponse<String> response =
                client.send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(404, response.statusCode());
    }

    // ---------- GET /movies?year=YYYY ----------

    @Test
    void getMoviesByYear_returnsMovies() throws Exception {
        addMovie("Movie2020", 2020);
        addMovie("Movie2021", 2021);

        HttpResponse<String> response = sendGet("/movies?year=2020");
        List<Movie> movies =
                gson.fromJson(response.body(), new ListOfMoviesTypeToken().getType());

        assertEquals(200, response.statusCode());
        assertEquals(1, movies.size());
        assertEquals(2020, movies.get(0).getYear());
    }

    @Test
    void getMoviesByYear_returns400_whenYearInvalid() throws Exception {
        HttpResponse<String> response = sendGet("/movies?year=abc");

        assertEquals(400, response.statusCode());
    }

    // ---------- Вспомогательные методы ----------

    private static Movie addMovie(String title, int year) throws Exception {
        String body = "{ \"title\": \"" + title + "\", \"year\": " + year + " }";

        HttpResponse<String> response = sendPost("/movies", body);
        return gson.fromJson(response.body(), Movie.class);
    }

    private static HttpResponse<String> sendGet(String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + path))
                .GET()
                .build();

        return client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private static HttpResponse<String> sendPost(String path, String body) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + path))
                .header("Content-Type", "application/json; charset=UTF-8")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        return client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }
}

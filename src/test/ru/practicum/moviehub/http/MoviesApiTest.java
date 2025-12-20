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

class MoviesApiTest {

    private static HttpClient client;
    private static final Gson gson = new Gson();

    private static final String BASE_URL = "http://localhost:8080";
    private static final String MOVIES_PATH = "/movies";
    private static final String JSON_CONTENT_TYPE = "application/json; charset=UTF-8";

    private MoviesServer server;
    private MoviesStore store;

    @BeforeAll
    static void beforeAll() {
        client = HttpClient.newHttpClient();
    }

    @BeforeEach
    void setUp() {
        store = new MoviesStore();
        server = new MoviesServer(store, 8080);
        server.start();
    }

    @AfterEach
    void tearDown() {
        server.stop();
    }

    // ---------- POST /movies ----------

    @Test
    void postMovies_addsMovie_whenValid() throws Exception {
        String body = "{ \"title\": \"Inception\", \"year\": 2010 }";

        HttpResponse<String> response = sendPost(MOVIES_PATH, body);
        Movie movie = gson.fromJson(response.body(), Movie.class);

        assertEquals(201, response.statusCode());
        assertEquals(JSON_CONTENT_TYPE, response.headers().firstValue("Content-Type").orElse(null));
        assertNotNull(movie.getId());
        assertEquals("Inception", movie.getTitle());
    }

    @Test
    void postMovies_returns422_whenTitleEmpty() throws Exception {
        String body = "{ \"title\": \"\", \"year\": 2000 }";

        HttpResponse<String> response = sendPost(MOVIES_PATH, body);

        assertEquals(422, response.statusCode());
        assertEquals(JSON_CONTENT_TYPE, response.headers().firstValue("Content-Type").orElse(null));
    }

    @Test
    void postMovies_returns422_whenInvalidJson() throws Exception {
        String body = "{ title: Inception }"; // некорректный JSON

        HttpResponse<String> response = sendPost(MOVIES_PATH, body);

        assertEquals(422, response.statusCode());
        assertEquals(JSON_CONTENT_TYPE, response.headers().firstValue("Content-Type").orElse(null));
    }

    @Test
    void postMovies_returns415_whenWrongContentType() throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + MOVIES_PATH))
                .header("Content-Type", "text/plain")
                .POST(HttpRequest.BodyPublishers.ofString("test"))
                .build();

        HttpResponse<String> response =
                client.send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(415, response.statusCode());
    }

    // ---------- GET /movies/{id} ----------

    @Test
    void getMovieById_returnsMovie_whenExists() throws Exception {
        Movie movie = addMovie("Avatar", 2009);

        HttpResponse<String> response = sendGet(MOVIES_PATH + "/" + movie.getId());
        Movie result = gson.fromJson(response.body(), Movie.class);

        assertEquals(200, response.statusCode());
        assertEquals(JSON_CONTENT_TYPE, response.headers().firstValue("Content-Type").orElse(null));
        assertEquals(movie.getId(), result.getId());
    }

    @Test
    void getMovieById_returns404_whenNotFound() throws Exception {
        HttpResponse<String> response = sendGet(MOVIES_PATH + "/9999");

        assertEquals(404, response.statusCode());
    }

    // ---------- GET /movies?year=YYYY ----------

    @Test
    void getMoviesByYear_returnsMovies() throws Exception {
        addMovie("Movie2020", 2020);
        addMovie("Movie2021", 2021);

        HttpResponse<String> response = sendGet(MOVIES_PATH + "?year=2020");
        List<Movie> movies =
                gson.fromJson(response.body(), new ListOfMoviesTypeToken().getType());

        assertEquals(200, response.statusCode());
        assertEquals(JSON_CONTENT_TYPE, response.headers().firstValue("Content-Type").orElse(null));
        assertEquals(1, movies.size());
        assertEquals(2020, movies.get(0).getYear());
    }

    @Test
    void unsupportedMethod_returns405() throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + MOVIES_PATH))
                .PUT(HttpRequest.BodyPublishers.noBody())
                .build();

        HttpResponse<String> response =
                client.send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(405, response.statusCode());
    }

    // ---------- Вспомогательные методы ----------

    private Movie addMovie(String title, int year) throws Exception {
        String body = "{ \"title\": \"" + title + "\", \"year\": " + year + " }";
        HttpResponse<String> response = sendPost(MOVIES_PATH, body);
        return gson.fromJson(response.body(), Movie.class);
    }

    private HttpResponse<String> sendGet(String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + path))
                .GET()
                .build();

        return client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private HttpResponse<String> sendPost(String path, String body) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + path))
                .header("Content-Type", JSON_CONTENT_TYPE)
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        return client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }
}

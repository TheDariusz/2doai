package com.thedariusz.todoai;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import javax.sql.DataSource;

import io.restassured.http.ContentType;
import io.restassured.http.Cookie;
import io.restassured.response.Response;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.web.authentication.rememberme.PersistentRememberMeToken;
import org.springframework.security.web.authentication.rememberme.PersistentTokenRepository;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;

/**
 * The long session (passwordless sign-in, release 1): a user stays signed in across a browser restart
 * and a backend deploy, through a {@code remember-me} cookie backed by the {@code remember_me_token}
 * table.
 *
 * <p>A deploy or a browser restart both look the same from here: the request arrives with no live
 * {@code HttpSession} behind it. Every case below reproduces that by sending the remember-me cookie
 * alone (or beside a session id the server no longer knows), which is exactly what the browser does.
 *
 * <p>The two properties this class is really about are the ones that are easy to lose silently:
 * <ul>
 *   <li><b>the database stays asleep</b> — a request with a live session never reads the token table,
 *       so Neon can still suspend with a signed-in tab open (lessons.md); and</li>
 *   <li><b>the token never rotates</b> — Spring's stock persistent services rewrite the value on every
 *       auto-login, so two requests carrying one cookie (two restored tabs, React StrictMode's double
 *       bootstrap) would read as cookie theft, wipe every token of the user and answer 500.</li>
 * </ul>
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RememberMeApiTest extends ApiTestBase {

	private static final String REMEMBER_ME = "remember-me";

	private static final long NINETY_DAYS = Duration.ofDays(90).toSeconds();

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private DataSource dataSource;

	@Autowired
	private PersistentTokenRepository tokens;

	@Test
	void signingInSetsAnHttpOnlySameSiteStrictRememberMeCookieForNinetyDays() {
		String email = uniqueEmail();
		register(email, "correct-horse");

		Cookie remembered = login(email, "correct-horse").statusCode(201).extract().detailedCookie(REMEMBER_ME);

		assertThat(remembered.getValue()).isNotBlank();
		assertThat(remembered.isHttpOnly()).isTrue();
		assertThat(remembered.getSameSite()).isEqualTo("Strict");
		assertThat(remembered.getMaxAge()).isEqualTo(NINETY_DAYS);
		assertThat(remembered.getPath()).isEqualTo("/");
	}

	/** Both credentials share one session-creation path, so both are remembered. */
	@Test
	void aCodeSignInIsRememberedToo() {
		String email = uniqueEmail();
		register(email, "correct-horse");

		Cookie remembered = signInWithCode(email, mailedSignInCode(email))
				.statusCode(201)
				.extract()
				.detailedCookie(REMEMBER_ME);

		assertThat(remembered.getValue()).isNotBlank();
		assertThat(remembered.getMaxAge()).isEqualTo(NINETY_DAYS);
	}

	/**
	 * Tomcat fixes a session cookie's {@code Max-Age} when the session is created and never moves it,
	 * so a lifetime here would expire the session N days after sign-in however active the user was.
	 * The session cookie stays a browser-session cookie; the remember-me cookie carries persistence.
	 */
	@Test
	void theSessionCookieCarriesNoMaxAge() {
		String email = uniqueEmail();
		register(email, "correct-horse");

		Cookie session = login(email, "correct-horse").statusCode(201).extract().detailedCookie("JSESSIONID");

		assertThat(session.hasMaxAge()).isFalse();
		assertThat(session.hasExpiryDate()).isFalse();
	}

	/**
	 * The deploy case. The token table is read once, on the request that finds no session; that request
	 * gets a session of its own, and from then on the session answers in memory as it always did.
	 */
	@Test
	void aRequestCarryingOnlyTheRememberMeCookieIsSignedInAndTheNextOneCostsNoQuery() {
		String email = uniqueEmail();
		String remembered = rememberedSignIn(email);

		Response autoLogin = given().cookie(REMEMBER_ME, remembered).when().get("/api/users/me");

		autoLogin.then().statusCode(200).body("email", equalTo(email));
		String session = autoLogin.getCookie("JSESSIONID");
		assertThat(session).isNotBlank();

		long before = connectionsBorrowed();
		given().cookie("JSESSIONID", session).when().get("/api/users/me").then().statusCode(200);
		assertThat(connectionsBorrowed()).as("connections taken by a request with a live session").isEqualTo(before);
	}

	/**
	 * The idleness rule (lessons.md): with a live session the remember-me cookie rides along on every
	 * request and must never be looked at. If it were, a signed-in tab left open would hold Neon awake.
	 */
	@Test
	void aRequestWithALiveSessionNeverReadsTheTokenTable() {
		givenLoggedInUser();

		long before = connectionsBorrowed();
		client().when().get("/api/users/me").then().statusCode(200);
		client().when().get("/api/users/me").then().statusCode(200);

		assertThat(connectionsBorrowed()).isEqualTo(before);
	}

	/**
	 * After a deploy the browser still holds its old {@code XSRF-TOKEN} cookie. The auto-login retires
	 * it ({@code CsrfAuthenticationStrategy}) and only defers the replacement, so unless something reads
	 * the new token on the way out the response carries a deletion and nothing else — and the SPA's
	 * first save after a deploy fails before it is even sent.
	 */
	@Test
	void anAutoLoginHandsOutAFreshCsrfTokenAndTheNextMutationSucceeds() {
		String email = uniqueEmail();
		register(email, "correct-horse");
		Response signIn = csrfAware()
				.body(Map.of("email", email, "password", "correct-horse"))
				.when()
				.post("/api/sessions");
		String remembered = signIn.getCookie(REMEMBER_ME);
		String csrfBeforeTheDeploy = signIn.getCookie("XSRF-TOKEN");
		assertThat(csrfBeforeTheDeploy).isNotBlank();

		Response autoLogin = given()
				.cookie(REMEMBER_ME, remembered)
				.cookie("XSRF-TOKEN", csrfBeforeTheDeploy)
				.cookie("JSESSIONID", "a-session-the-restarted-server-never-issued")
				.when()
				.get("/api/users/me");

		autoLogin.then().statusCode(200);
		String fresh = autoLogin.getCookie("XSRF-TOKEN");
		assertThat(fresh).as("XSRF-TOKEN on the auto-login response").isNotBlank();

		given()
				.cookie("JSESSIONID", autoLogin.getCookie("JSESSIONID"))
				.cookie("XSRF-TOKEN", fresh)
				.header("X-XSRF-TOKEN", fresh)
				.contentType(ContentType.JSON)
				.body(Map.of("language", "EN"))
				.when()
				.patch("/api/users/me")
				.then()
				.statusCode(200);
	}

	/**
	 * Two restored tabs, or StrictMode bootstrapping twice in dev: the same cookie arrives on two
	 * requests at once. A rotating token lets the first one win and reads the second as theft.
	 */
	@Test
	void twoConcurrentRequestsCarryingOneCookieBothSucceedAndTheTokenSurvives() throws Exception {
		String email = uniqueEmail();
		String remembered = rememberedSignIn(email);

		ExecutorService pool = Executors.newFixedThreadPool(2);
		try {
			CountDownLatch start = new CountDownLatch(1);
			List<Future<Integer>> statuses = List.of(
					pool.submit(() -> autoLoginStatus(remembered, start)),
					pool.submit(() -> autoLoginStatus(remembered, start)));
			start.countDown();

			for (Future<Integer> status : statuses) {
				assertThat(status.get()).isEqualTo(200);
			}
		}
		finally {
			pool.shutdownNow();
		}

		given().cookie(REMEMBER_ME, remembered).when().get("/api/users/me").then().statusCode(200);
		assertThat(tokenRowsOf(email)).isEqualTo(1);
	}

	/** The lifetime counts from the last use, not from sign-in: every auto-login renews it. */
	@Test
	void anAutoLoginRenewsTheCookieWithTheSameValueAndAFreshNinetyDays() {
		String email = uniqueEmail();
		String remembered = rememberedSignIn(email);
		jdbc.update("update remember_me_token set last_used_at = now() - interval '10 days' "
				+ "where user_id = (select id from app_user where email = ?)", email);

		Cookie renewed = given()
				.cookie(REMEMBER_ME, remembered)
				.when()
				.get("/api/users/me")
				.then()
				.statusCode(200)
				.extract()
				.detailedCookie(REMEMBER_ME);

		assertThat(renewed.getValue()).isEqualTo(remembered);
		assertThat(renewed.getMaxAge()).isEqualTo(NINETY_DAYS);
		assertThat(renewed.getSameSite()).isEqualTo("Strict");
		Boolean touched = jdbc.queryForObject("select last_used_at > now() - interval '1 minute' "
				+ "from remember_me_token where user_id = (select id from app_user where email = ?)",
				Boolean.class, email);
		assertThat(touched).isTrue();
	}

	@Test
	void logoutDeletesTheTokenAndClearsTheCookie() {
		String email = uniqueEmail();
		register(email, "correct-horse");
		String remembered = login(email, "correct-horse").statusCode(201).extract().cookie(REMEMBER_ME);

		Cookie cleared = csrfAware()
				.when()
				.delete("/api/sessions/current")
				.then()
				.statusCode(204)
				.extract()
				.detailedCookie(REMEMBER_ME);

		assertCleared(cleared);
		assertThat(cleared.getSameSite()).isEqualTo("Strict");
		assertThat(tokenRowsOf(email)).isZero();
		given().cookie(REMEMBER_ME, remembered).when().get("/api/users/me").then().statusCode(401);
	}

	@Test
	void deletingTheAccountRevokesTheRememberMeCookie() {
		String email = uniqueEmail();
		register(email, "correct-horse");
		String remembered = login(email, "correct-horse").statusCode(201).extract().cookie(REMEMBER_ME);

		Cookie cleared = csrfAware()
				.body(Map.of("password", "correct-horse"))
				.when()
				.delete("/api/users/me")
				.then()
				.statusCode(204)
				.extract()
				.detailedCookie(REMEMBER_ME);

		assertCleared(cleared);
		given().cookie(REMEMBER_ME, remembered).when().get("/api/users/me").then().statusCode(401);
	}

	/**
	 * Every way a cookie can be wrong is the same anonymous 401, and the cookie is cleared so the
	 * browser stops sending it. Never a 500: Spring's stock services raise {@code CookieTheftException}
	 * above the exception translation, which is a server error for a client's bad cookie.
	 */
	@Test
	void aForgedOrUnknownTokenIsAnAnonymous401AndIsCleared() {
		String email = uniqueEmail();
		String remembered = rememberedSignIn(email);
		String series = decode(remembered)[0];

		for (String forged : List.of("%%%not-base64%%%", encode("no-such-series", "no-such-value"),
				encode(series, "not-the-value"), encode("only-one-part"))) {
			Cookie cleared = given()
					.cookie(REMEMBER_ME, forged)
					.when()
					.get("/api/users/me")
					.then()
					.statusCode(401)
					.extract()
					.detailedCookie(REMEMBER_ME);
			assertCleared(cleared);
		}

		// A wrong value for a real series is a refused guess, not grounds to sign the owner out.
		given().cookie(REMEMBER_ME, remembered).when().get("/api/users/me").then().statusCode(200);
	}

	@Test
	void aTokenUnusedForNinetyDaysNoLongerSignsIn() {
		String email = uniqueEmail();
		String remembered = rememberedSignIn(email);
		jdbc.update("update remember_me_token set last_used_at = now() - interval '91 days' "
				+ "where user_id = (select id from app_user where email = ?)", email);

		given().cookie(REMEMBER_ME, remembered).when().get("/api/users/me").then().statusCode(401);
	}

	/** The token is a way back into a session, and an unproved address cannot hold one. */
	@Test
	void aTokenForAnAccountWhoseAddressIsUnprovedIsRefused() {
		String email = uniqueEmail();
		csrfAware()
				.body(Map.of("email", email, "password", "correct-horse"))
				.when()
				.post("/api/users")
				.then()
				.statusCode(201);
		tokens.createNewToken(new PersistentRememberMeToken(email, "unprovedseries", "unprovedvalue", new Date()));

		given()
				.cookie(REMEMBER_ME, encode("unprovedseries", "unprovedvalue"))
				.when()
				.get("/api/users/me")
				.then()
				.statusCode(401);
	}

	/** There is no cleanup job (Neon idleness): a user's dead tokens go when that user next signs in. */
	@Test
	void signingInSweepsTheUsersExpiredTokens() {
		String email = uniqueEmail();
		register(email, "correct-horse");
		tokens.createNewToken(new PersistentRememberMeToken(email, "expiredseries", "expiredvalue",
				Date.from(new Date().toInstant().minus(Duration.ofDays(91)))));

		login(email, "correct-horse").statusCode(201);

		assertThat(tokens.getTokenForSeries("expiredseries")).isNull();
		assertThat(tokenRowsOf(email)).isEqualTo(1);
	}

	private String rememberedSignIn(String email) {
		register(email, "correct-horse");
		String remembered = login(email, "correct-horse").statusCode(201).extract().cookie(REMEMBER_ME);
		assertThat(remembered).isNotBlank();
		return remembered;
	}

	private int autoLoginStatus(String remembered, CountDownLatch start) throws InterruptedException {
		start.await();
		return given().cookie(REMEMBER_ME, remembered).when().get("/api/users/me").statusCode();
	}

	/**
	 * A cancel cookie. Tomcat renders a zero {@code Max-Age} as an {@code Expires} in 1970 and no
	 * {@code Max-Age} attribute at all, so the expiry is what is checked.
	 */
	private static void assertCleared(Cookie cookie) {
		assertThat(cookie).as("a cancel cookie for %s", REMEMBER_ME).isNotNull();
		assertThat(cookie.getValue()).isEmpty();
		assertThat(cookie.getExpiryDate()).isBefore(new Date());
	}

	private long connectionsBorrowed() {
		return ((TestcontainersConfiguration.ConnectionCounter) dataSource).borrowed();
	}

	private int tokenRowsOf(String email) {
		return jdbc.queryForObject("select count(*) from remember_me_token "
				+ "where user_id = (select id from app_user where email = ?)", Integer.class, email);
	}

	/** The cookie's wire format: Base64 of the parts joined by {@code :}, padding stripped. */
	private static String encode(String... parts) {
		return Base64.getEncoder().withoutPadding()
				.encodeToString(String.join(":", parts).getBytes(StandardCharsets.UTF_8));
	}

	private static String[] decode(String cookie) {
		String padded = cookie + "=".repeat((4 - cookie.length() % 4) % 4);
		return new String(Base64.getDecoder().decode(padded), StandardCharsets.UTF_8).split(":");
	}
}

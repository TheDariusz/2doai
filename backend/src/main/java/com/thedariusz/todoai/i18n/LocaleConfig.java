package com.thedariusz.todoai.i18n;

import java.util.List;
import java.util.Locale;

import com.thedariusz.todoai.user.AppLanguage;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.i18n.AcceptHeaderLocaleResolver;

/**
 * How a request says which language it wants answered in (DEV-49).
 *
 * <p>{@code Accept-Language} is the transport and {@code app_user.preferred_language} is the
 * persistence. The SPA sets the header from whatever it is currently rendering, so the server never
 * has to look an account up to know what to write — which is what lets {@code GET /api/users/me}
 * keep answering with no query at all (the Neon idleness rule in
 * {@code context/foundation/lessons.md}).
 *
 * <p><b>Spring's own resolver does the negotiation, deliberately.</b> {@code Accept-Language} is not
 * a single tag — it is a quality-ordered list, and a browser's first choice is regularly one the app
 * does not speak. {@link AcceptHeaderLocaleResolver} already walks that list in order, matches on
 * language while ignoring region, and survives malformed input; a hand-rolled "read the first tag"
 * parser answers {@code fr-FR;q=0.9,pl;q=0.8} wrongly, which is the case
 * {@code AuthApiTest.registrationHonoursQualityOrderingWhenTheFirstChoiceIsUnsupported} pins.
 *
 * <p>Handlers then read the outcome by declaring a plain {@link Locale} parameter — Spring MVC
 * resolves it through this bean — and turn it into the domain type with {@link AppLanguage#of}.
 * There is deliberately no request-scoped holder to read it from: passing the language down as an
 * argument keeps the services that render text testable without a servlet, and keeps the resolved
 * language visible in the signature of everything that depends on it.
 *
 * <p>The bean has to be named {@code localeResolver} — that is the name {@code DispatcherServlet}
 * looks it up under, and it is what makes Boot's own auto-configured resolver back off.
 */
@Configuration
public class LocaleConfig {

	@Bean
	LocaleResolver localeResolver() {
		AcceptHeaderLocaleResolver resolver = new AcceptHeaderLocaleResolver();
		resolver.setSupportedLocales(List.of(AppLanguage.EN.locale(), AppLanguage.PL.locale()));
		// Also what an absent or blank header resolves to. Without it the resolver falls through to
		// the server's own default locale, which would make the answer depend on the machine the JVM
		// happens to run on — Polish on this laptop, English on Fly. A header that is *present* but
		// names no parseable tag still takes that fall-through, because the short-circuit tests the
		// header for text rather than for meaning; no browser sends one, and closing it would cost
		// more than the case is worth.
		resolver.setDefaultLocale(AppLanguage.DEFAULT.locale());
		return resolver;
	}
}

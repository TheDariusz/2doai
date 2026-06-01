# 2do AI - Todo lista oraz asystent planowania z AI



### Główny problem:

Więkoszość aplikacji Todo skupia sie na bieżących zadaniach, a planowanie bardziej przyszlościowych celów jest poza ich zakresem. Listy zadań nie mają też żadnej wiedzy o użytkowniku przez co zadania są oderwane od tego jak wygląda życie użytkownika i nie są w stanie mu pomóc w osiągnięciu długoterminowych celów. Programy te wyrywkowo nie przypominają również o ważnych celach które są długoterminowe.

### Najmniejszy zakres funkcjonalny:

* zarządzanie zadaniami bieżącymi, długoterminowymii (ten rok, parę miesiecy do przodu), bez określonych ram czasowych (marzenia do realizacji "kiedyś")
* zadania bieżące tworzone na podstawie dowolnych źródeł, w tym prosta wiadomość od użytkownia, link do ciekawego artykułu = zadanie "przeczytaj" albo AI po analizie teksu sugeruje "tu są najważniesze punkty z tego materiału, pomyśl jak to wdrożyć u siebie", kalendarz, itd
* zadania długoterminowe tworzone przez użytkownika np. "chciałbym w tym roku ukończyć prawo jazdy" 
* marzenia tworzone przez użytkownika np. "chciałbym pojechać do Japonii kiedyś" 
* AI losowow wybiera zadania długoterminowe i marzenia do tej pory niepodjęte przez użytkownia i co jakiś czas zadanie pytanie do użytkownika czy może miałby ochotę zacząć działać w tym temaci
* AI pomaga w realizacji zadań długoterminowych i marzen - wyszukuje w internecie potrzebne informacje, podpowiada kroki ktore pomoga w realizacji celu
* AI buduje pamięc o uzytkowniku (baza danych, plik md), jego zachowaniach, jego preferencjach, zrealizowane zadania pomogaja w budowaniu tej pamięci
* Aplikacja działa online (z AI, bazą danych "w chmurze", dostepem do internetu) i offline (zadania w formacie plikow md lokalnie przechowywanych, pamięc lokalnie w pliku md, offlne bez dostępu do AI)
* Interfejs webowy (www, mobile) z podłączenim do Telegram

### Co nie wchodzi w zakres MVP:

* AI nie zarządza czasem użytkownika - może mieć dostep do kalendarza ale nie optymalizuje jego
* Aplikacja w trybie offline bez lokalnego modelu AI
* Powiadomienia i "czat z AI" poprzez interfejs webowy i maile
* Brak możliwości rozmowy z AI poprzez czat głosowy

### Kryteria sukcesu:

* zadania krótkoterminowe i długoterminowe pod kontrolą AI

  


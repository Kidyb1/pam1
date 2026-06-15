# Shaper – Twój Inteligentny Asystent Formy

Shaper to nowoczesna aplikacja na Androida służąca do śledzenia postępów sylwetkowych, która integruje dane o żywieniu i wadze z ekosystemem **Health Connect**. Aplikacja nie tylko gromadzi dane, ale analizuje je, aby dostarczać prognozy i spersonalizowane wskazówki trenerskie.

##  Główne Funkcje

*   **Synchronizacja z Health Connect:** Automatyczne pobieranie danych o kaloriach i makroskładnikach (białko, tłuszcze, węglowodany) z aplikacji takich jak Fitatu czy MyFitnessPal.
*   **Zaawansowana Analiza Wagi:** 
    *   Wykres postępu z linią trendu opartą na **regresji liniowej**.
    *   Automatyczna prognoza daty osiągnięcia celu na podstawie rzeczywistego tempa zmian.
    *   Wizualizacja linii celu i planowanego tempa (Diet Pace).
*   **Dynamiczne Makroskładniki:** Monitorowanie spożycia B/T/W w czasie rzeczywistym z czytelnymi paskami postępu.
*   **System Podpowiedzi Trenera:** Spersonalizowane wskazówki generowane na podstawie wybranego celu (Redukcja, Masa, Utrzymanie) i stażu w procesie.
*   **Zarządzanie Profilem:** Obliczanie zapotrzebowania kalorycznego i dostosowywanie tempa diety.

##  Stos Technologiczny

*   **Język:** Kotlin
*   **UI:** Jetpack Compose (Material 3)
*   **Architektura:** MVVM (ViewModel, StateFlow, Coroutines)
*   **Baza danych/Backend:** Firebase (Auth, Firestore)
*   **Zdrowie:** Health Connect API (wymaga Android 8.0+)
*   **Nawigacja:** Jetpack Navigation Compose
*   **Analiza danych:** Regresja liniowa dla predykcji trendów wagi.

##  Integracja z Health Connect

Shaper wykorzystuje najnowsze API Health Connect do agregacji danych. 
*   **Wymagania:** `minSdkVersion 26`.
*   **Uprawnienia:** `READ_NUTRITION` (dla kalorii i makro) oraz `READ_WEIGHT` (dla wagi).
*   **Diagnostyka:** Aplikacja zawiera wbudowany system logowania (logcat) oraz powiadomienia UI (Snackbar), które informują o statusie synchronizacji i ewentualnych brakach uprawnień w aplikacjach źródłowych (np. Fitatu).

##  Jak zacząć?

1.  Zaloguj się/Zarejestruj przez Firebase.
2.  Przejdź onboarding, aby obliczyć swoje zapotrzebowanie.
3.  Na głównym panelu kliknij **"Połącz z Health"**, aby nadać uprawnienia.
4.  Upewnij się, że Twoja aplikacja licznik kalorii (np. Fitatu) ma włączony **zapis** do Health Connect w ustawieniach systemu Android.
5.  Używaj przycisku odświeżania na górnym pasku, aby wymusić synchronizację danych z danego dnia.

##  Status Projektu
Aplikacja jest w fazie aktywnego rozwoju. Ostatnio zaimplementowano:
- [x] Pełną agregację makroskładników.
- [x] System predykcji wagi na wykresie.
- [x] Obsługę błędów synchronizacji i powiadomienia dla użytkownika.

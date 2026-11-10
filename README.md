# Generatory (Paper 1.21+, Java 21)

Obszarowe generatory (regenerujace sie kopalnie): zaznaczasz obszar rozdzka, tworzysz generator komenda,
ustawiasz wszystko w GUI. Gracze normalnie kopia, a gdy obszar zostanie wykopany, generator odnawia sie sam.

## Budowanie
Wymagania: JDK 21 i Maven.

    mvn clean package

Gotowy plik: `target/Generatory-1.0.0.jar` -> wrzuc do `plugins/` (Paper 1.21+).
PlaceholderAPI jest opcjonalne.

## Szybki start
1. `/generator wand`  - dostajesz rozdzke (LPM = pozycja 1, PPM = pozycja 2; widzisz ramke z czasteczek)
2. `/generator create kopalnia`  - tworzy generator i otwiera GUI ustawien
3. W GUI: interwal, tryb odliczania, typ animacji, czasteczki, dzwiek, bloki i szanse itd.

## Komendy (uprawnienie `generator.admin`; alias: /gen)
wand, pos1, pos2, create <nazwa>, delete <nazwa>, list, info [nazwa], gui [nazwa], regen [nazwa],
recount [nazwa], tp <nazwa>, setinterval [nazwa] <sek>, settype [nazwa] <typ>, toggle [nazwa],
addblock <nazwa> <BLOK> [waga], removeblock <nazwa> <BLOK>, reload.
Stojac wewnatrz generatora mozesz pominac nazwe.

`generator.bypass` pozwala stawiac bloki w chronionym generatorze.

## Typy regeneracji
WAVE (fala warstwami), CENTER_OUT (od srodka), RANDOM_POP (losowe), GROWING (rosnace bloki - Block Display), INSTANT.

## Tryby odliczania
- WHEN_EMPTY: gdy wykopano zadany % obszaru (domyslnie 100%), rusza odliczanie, potem regeneracja
- PERIODIC: regeneracja co X sekund niezaleznie od wykopania

## Placeholdery (PlaceholderAPI)
`%generator_<nazwa>_<klucz>%` oraz `%generator_nearest_<klucz>%` (generator, w ktorym stoisz / najblizszy)
Klucze: time, time_formatted, status, progress, percent, blocks_left, blocks_total, blocks_mined,
regen_type, interval, trigger, enabled, world, size, name. Dodatkowo `%generator_count%`.
Nazwy generatorow: litery, cyfry, myslnik (bez podkreslenia).

## Pliki
config.yml (ustawienia globalne i wartosci domyslne), messages.yml (wszystkie teksty), generators.yml (dane).

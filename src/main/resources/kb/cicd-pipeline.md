---
title: CI/CD torustik
topic: cicd
aliases: [ci/cd, cicd, torustik, pipeline, build, ehitus, automaattestid]
---

## Torustiku etapid

Torustik koosneb neljast etapist: build, unit testid, staatiline
analüüs ja deploy. Iga etapp peab läbima enne järgmise käivitumist.

## Torustiku käivitamine

Torustik käivitub automaatselt iga push'i peale ning ka iga
merge request'i loomisel. Käsitsi käivitamine on võimalik
CI/CD tööriista "Run pipeline" nupuga.

## Ebaõnnestunud torustik

Kui torustik ebaõnnestub, uuri logisid vastava etapi alt. Levinuim
põhjus on ebaõnnestunud unit testid või koodistiili reeglite rikkumine.

## Konfiguratsioon

Torustiku sammud on kirjeldatud repositooriumi juurkaustas failis
`.ci-config.yml`. Muudatused sellesse faili vajavad koodireview't
samamoodi nagu rakenduse kood.

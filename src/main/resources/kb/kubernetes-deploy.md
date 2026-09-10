---
title: Kubernetesi deploy protsess
topic: kubernetes
aliases: [kubernetes, k8s, deploy, deployment, rakenduse paigaldamine, klaster]
---

## Deploy protsessi ülevaade

Rakenduse paigaldamine Kubernetesi klastrisse käib CI/CD torustiku kaudu:
pärast koodimuudatuse mergimist ehitab torustik konteineripildi ja
rakendab Helm chart'i vastavasse keskkonda (dev, staging, prod).

## Keskkonnad

Dev-keskkonda deploy'itakse automaatselt igal mergil main-harusse.
Staging ja prod vajavad käsitsi kinnitust vastutavalt tiimijuhilt.

## Deploy oleku jälgimine

Deploy olekut saab jälgida CI/CD torustiku vaates või käsuga
`kubectl rollout status deployment/<nimi> -n <namespace>`.

## Vea korral tagasipööramine

Ebaõnnestunud deploy'i saab tagasi pöörata eelmisele töötavale
versioonile CI/CD torustiku "Rollback" nupuga või käsuga
`kubectl rollout undo deployment/<nimi>`.

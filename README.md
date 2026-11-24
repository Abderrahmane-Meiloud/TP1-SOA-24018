# TP1 SOA — SOAP/WSDL (approche Contract-First)

Service web SOAP « Emprunt de livres » développé selon l'approche **Contract-First** : le contrat WSDL (`EmpruntService.wsdl`) est écrit en premier, puis les classes Java sont générées à partir de ce contrat (`wsimport`) avant d'être implémentées.

## Contenu du dépôt

- `emprunt-contractfirst/` — projet Maven du service (WSDL dans `src/main/resources/wsdl/`, implémentation Java dans `src/main/java/`, `pom.xml`).
- `soapui/` — preuves de test SoapUI : projet `ContractFirst-soapui-project.xml`, requêtes (`requests/`), réponses (`responses/`), statuts HTTP (`status/`) et résultats bruts (`raw-results/`).
- `xml/` — exemples de messages SOAP (enveloppe, fault, etc.).
- `reponses.md` — réponses détaillées aux questions du TP.
- `SupNum_SOA_TP1_SOAP_WSDL.pdf` — énoncé du TP.

## Compiler le service

Prérequis : JDK 17+ et Maven.

```bash
cd emprunt-contractfirst
mvn clean package
```

La compilation déclenche le plugin `jaxws-maven-plugin` (goal `wsimport`), qui génère les classes Java à partir du contrat `src/main/resources/wsdl/EmpruntService.wsdl`.

## Démarrer le service

```bash
cd emprunt-contractfirst
mvn exec:java
```

Le service est publié sur :

```
http://localhost:8080/bib/EmpruntService
```

Le WSDL (issu du contrat d'origine) est consultable à l'adresse :

```
http://localhost:8080/bib/EmpruntService?wsdl
```

## Importer et exécuter le projet SoapUI

1. Démarrer le service (voir ci-dessus) avant de lancer les tests.
2. Ouvrir SoapUI, puis **File > Import Project**.
3. Sélectionner le fichier `soapui/ContractFirst-soapui-project.xml`.
4. Exécuter les TestSuites du projet : les requêtes envoyées, les réponses reçues et les statuts HTTP correspondent aux fichiers conservés dans `soapui/requests/`, `soapui/responses/` et `soapui/status/` (preuves réelles d'exécution, non modifiées).

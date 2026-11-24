# TP1 SOA — SOAP/WSDL (approche Code-First)

Service web SOAP « Emprunt de livres » développé selon l'approche **Code-First** (JAX-WS / `jakarta.xml.ws.Endpoint`) : le contrat WSDL est généré automatiquement à partir de l'implémentation Java.

## Contenu du dépôt

- `emprunt-codefirst/` — projet Maven du service (code source Java, `pom.xml`).
- `soapui/` — preuves de test SoapUI : projet `CodeFirst-soapui-project.xml`, requêtes (`requests/`), réponses (`responses/`), statuts HTTP (`status/`) et résultats bruts (`raw-results/`).
- `xml/` — exemples de messages SOAP (enveloppe, fault, etc.).
- `reponses.md` — réponses détaillées aux questions du TP.
- `SupNum_SOA_TP1_SOAP_WSDL.pdf` — énoncé du TP.

## Compiler le service

Prérequis : JDK 17+ et Maven.

```bash
cd emprunt-codefirst
mvn clean package
```

## Démarrer le service

```bash
cd emprunt-codefirst
mvn exec:java
```

Le service est publié sur :

```
http://localhost:8080/bib/EmpruntService
```

Le WSDL généré est consultable à l'adresse :

```
http://localhost:8080/bib/EmpruntService?wsdl
```

## Importer et exécuter le projet SoapUI

1. Démarrer le service (voir ci-dessus) avant de lancer les tests.
2. Ouvrir SoapUI, puis **File > Import Project**.
3. Sélectionner le fichier `soapui/CodeFirst-soapui-project.xml`.
4. Exécuter les TestSuites du projet : les requêtes envoyées, les réponses reçues et les statuts HTTP correspondent aux fichiers conservés dans `soapui/requests/`, `soapui/responses/` et `soapui/status/` (preuves réelles d'exécution, non modifiées).

# TP1 — SOAP et WSDL — Réponses

> **Note technique préalable (environnement d'exécution).** Deux ajustements, purement liés à l'environnement d'exécution (jamais à la logique métier imposée par l'énoncé), ont été nécessaires pour que les Faults SOAP se sérialisent correctement :
> 1. **`pom.xml`** (les deux projets) : `jaxws-rt:4.0.2` ne déclare sa dépendance `jaxb-impl → jaxb-runtime` qu'en portée `provided` (jamais héritée par un projet consommateur). Sans l'ajout explicite de `org.glassfish.jaxb:jaxb-runtime:4.0.6` en portée par défaut (`compile`), le serveur ne pouvait construire aucune Fault (`NoClassDefFoundError` sur `SOAPFaultBuilder`, confirmé dans les logs).
> 2. **`Serveur.java`** (les deux projets) : le pool de threads HTTP interne de `com.sun.net.httpserver` (utilisé par `Endpoint.publish`) ne reçoit pas toujours le classloader qui voit les dépendances Maven ajoutées par `exec:java`, ce qui faisait échouer la sérialisation JAXB/SAAJ des Faults (connexions fermées sans réponse, ou HTTP 500 sans corps). La solution retenue : publier l'Endpoint via `Endpoint.create(...)` puis lui fournir un `Executor` dédié (`Executors.newCachedThreadPool`) dont chaque thread reçoit explicitement, à sa création, le classloader qui a chargé la classe `Serveur` (celui qui voit toutes les dépendances Maven). Cette correction est strictement limitée au fichier `Serveur.java` (code d'amorçage, pas de logique métier) ; `EmpruntService`/`EmpruntServiceImpl`, leurs annotations et leurs opérations restent conformes à l'énoncé.
>
> Ces deux ajustements expliquent pourquoi `Serveur.java` diffère légèrement du squelette minimal donné en page 6 du sujet. Sans eux, les Faults requises par l'étape 4 ne pouvaient tout simplement pas être observées (réponses vides ou HTTP 500 sans corps) — confirmé par ré-exécution comparative avant/après correctif pour chaque scénario concerné (voir étapes 3 et 4 ci-dessous).
>
> **Validation XML (`xmllint`).** L'outil `xmllint` (paquet `libxml2-utils`) demandé par l'énoncé pour vérifier `xml/03-fault-sender-12.xml` n'a pas de distribution Windows officielle disponible dans cet environnement (aucun paquet `winget`, pas de `chocolatey` installé, et son installation via le script officiel a été bloquée par les protections de sécurité de l'environnement d'exécution — volontairement non contournées). À la place, la bibliothèque Python **`lxml`** a été utilisée : elle embarque le même moteur **`libxml2`** que `xmllint` (distribution officielle via PyPI) et a servi à exécuter exactement la même vérification que `xmllint --noout` effectue par défaut (analyse XML sans validation de schéma, échec si le document n'est pas bien formé) :
> ```python
> from lxml import etree
> etree.parse("xml/03-fault-sender-12.xml")   # lève XMLSyntaxError si le document n'est pas bien formé
> ```
> **Résultat réel** : `xml/03-fault-sender-12.xml` analysé avec succès (aucune exception) → document bien formé, équivalent de `xmllint --noout` retournant un code de sortie 0. Vérification de contrôle : le même script appliqué à un message volontairement mal formé (balises non appariées) lève bien une erreur de parsing, confirmant que le test détecte réellement les documents invalides. **`lxml` vérifie la bonne formation XML via `libxml2`, mais n'est pas l'exécutable `xmllint` littéral demandé par l'énoncé — cet écart est assumé et documenté ici, pas caché.**

## Partie A — Code-first

### Étape 1 — Créer et lancer le service

**Q1. Quelle annotation fait de `EmpruntService` un service web ? Quelle annotation fait de `LivreIndisponibleException` une erreur déclarée dans le contrat ?**

`@WebService` (placée sur la classe `EmpruntService`) en fait un service web JAX-WS : c'est elle qui déclenche la génération du WSDL par Metro au démarrage (`serviceName`, `portName` et `targetNamespace` deviennent le `<wsdl:service>`, le `<wsdl:port>` et l'espace de noms cible).

`@WebFault(name = "LivreIndisponible", targetNamespace = "http://supnum.mr/bib")` (placée sur `LivreIndisponibleException`) déclare cette exception comme une erreur *du contrat* : elle apparaît dans le WSDL comme un `<wsdl:fault>` de l'opération `emprunterLivre`, avec son propre message (`LivreIndisponibleException`) et son type de détail (`LivreIndisponibleInfo`), visible dans le `portType` et le `binding`. Sans `@WebFault`, l'exception resterait une erreur Java interne invisible du client.

**Q2. Pourquoi les classes de données doivent-elles avoir un constructeur vide ?**

Parce que JAXB (le moteur de (dé)sérialisation XML utilisé par JAX-WS) instancie les beans par réflexion lors du *unmarshalling* (XML → objet Java) : il appelle le constructeur sans argument, puis remplit les champs un par un. Sans constructeur vide accessible, JAXB ne peut pas construire l'objet et le service échoue dès le démarrage (ou à la première désérialisation) avec une erreur de type « no default constructor found ». C'est pourquoi `Emprunt` et `LivreIndisponibleInfo` ont toutes deux un constructeur `()` en plus de leur constructeur à paramètres (utilisé lui par *notre* code Java, pas par JAXB).

### Étape 2 — Lire le WSDL généré

**Q1. Quelle est l'URL du service ? Quelles sont les deux opérations et leurs messages d'entrée et de sortie ?**

URL du service : `http://localhost:8080/bib/EmpruntService` (élément `<soap:address>` dans `<service><port>`).

Deux opérations dans le `portType` (nommé `EmpruntService`, d'après le nom de la classe) :
- `emprunterLivre` — entrée : message `emprunterLivre` (élément `tns:emprunterLivre`) ; sortie : message `emprunterLivreResponse` (élément `tns:emprunterLivreResponse`) ; fault déclaré : message `LivreIndisponibleException` (élément `tns:LivreIndisponible`).
- `rendreLivre` — entrée : message `rendreLivre` (élément `tns:rendreLivre`) ; sortie : message `rendreLivreResponse` (élément `tns:rendreLivreResponse`) ; aucun fault déclaré dans le WSDL à ce stade (le `faultClient()` de l'ISBN mal formé n'est pas un fault *déclaré* du contrat — il est levé à la main via `SOAPFaultException`, donc invisible dans le WSDL).

**Q2. Quel style (`document`/`rpc`) et quel `use` (`literal`/`encoded`) le binding déclare-t-il ? SOAP 1.1 ou 1.2 ?**

`<soap:binding style="document" transport="http://schemas.xmlsoap.org/soap/http">` et `<soap:body use="literal">` sur chaque opération : c'est du **document/literal** (le style par défaut de JAX-WS, et le seul recommandé par WS-I Basic Profile). Namespace du binding : `http://schemas.xmlsoap.org/wsdl/soap/` → **SOAP 1.1**. Confirmé expérimentalement à l'étape 3 : un envoi avec l'enveloppe SOAP 1.2 (`http://www.w3.org/2003/05/soap-envelope`) produit un fault `VersionMismatch`, preuve que l'endpoint n'accepte que SOAP 1.1.

**Q3. Quel type XSD a été généré pour `isbn` et pour `dateRetour` ? La règle de format de l'ISBN apparaît-elle ?**

Les deux sont `xs:string` (minOccurs="0"), car en code-first JAXB dérive le type XSD uniquement du type Java du champ (`String isbn`, `String dateRetour` dans `Emprunt`/`LivreIndisponibleInfo`) — il n'y a ni restriction, ni `xs:date`. **La règle de format de l'ISBN n'apparaît pas dans le XSD** : le `Pattern` Java (`97[89]-\d-\d{2,5}-\d{2,6}-\d`) n'existe que dans le code de la méthode `emprunterLivre`, jamais exposée au contrat. Un client qui ne lit que le WSDL ne peut donc pas deviner le format attendu — c'est une limite classique du code-first (voir aussi la comparaison à l'étape 5, où le contrat écrit à la main expose `IsbnType` et `xs:date`).

**Q4. Dans la requête (SoapUI/équivalent), les paramètres `idAdherent` et `isbn` portent-ils un préfixe d'espace de noms ?**

Non. Seul l'élément wrapper de l'opération (`<bib:emprunterLivre>`) est qualifié par le namespace cible ; ses enfants `idAdherent` et `isbn` n'ont pas de préfixe, car le schéma généré ne déclare pas `elementFormDefault="qualified"` (confirmé par une requête réelle : `<idAdherent>A-117</idAdherent><isbn>978-1-234-5678-9</isbn>` sans préfixe a reçu une réponse 200 avec un numéro d'emprunt).

> **SoapUI** (5.10.0) a été installé et réellement piloté pour cette étape : *File › New SOAP Project* avec l'URL `http://localhost:8080/bib/EmpruntService?wsdl`, import réussi de l'interface `EmpruntPortBinding` (deux opérations détectées, `emprunterLivre` et `rendreLivre`), requête `emprunterLivre` remplie avec `idAdherent=A-117` et `isbn=978-1-234-5678-9` puis envoyée (réponse HTTP reçue, 288 octets), confirmé par les logs `soapui.log` horodatés de cette session. Le projet SoapUI exporté (`TP1-Emprunt-CodeFirst-soapui-project.xml`, requêtes des deux opérations, WSDL/XSD importés en cache) est conservé comme preuve dans le répertoire de travail, **à part de l'archive de rendu** — la structure de projet demandée par l'énoncé (page 3) ne prévoit pas ce fichier dans `emprunt-codefirst/`. Les observations ci-dessous (absence de préfixe, structure Raw) viennent de cette session SoapUI, recoupées avec `curl -v` sur la même requête pour la double vérification.

**Q5. Dans la trace brute, où voit-on HTTP, XML, SOAP ? Quel rôle joue chacun de ces standards, et quel rôle joue le WSDL ?**

Dans la trace `curl -v` on distingue trois couches empilées :
- **HTTP** : la ligne de requête (`POST /bib/EmpruntService HTTP/1.1`), les en-têtes (`Content-Type`, `SOAPAction`, `Host`) et la ligne de statut de la réponse (`HTTP/1.1 200 OK` / `500 Internal Server Error`).
- **XML** : la déclaration `<?xml version="1.0"?>` et la syntaxe générique (éléments, attributs, espaces de noms) qui sert de support à tout le reste.
- **SOAP** : la structure `<soap:Envelope><soap:Header/><soap:Body></soap:Body></soap:Envelope>` au-dessus du XML brut — c'est elle qui définit l'enveloppe, le mécanisme de Header optionnel et le format des Faults.

Rôles : **HTTP** transporte le message (protocole de transport, interchangeable — on pourrait utiliser JMS) ; **XML** sérialise les données de façon neutre vis-à-vis du langage ; **SOAP** est le protocole de messagerie qui structure l'échange (enveloppe, en-têtes, règles de Fault) indépendamment du transport ; le **WSDL**, lui, ne transite dans aucun message — c'est le contrat statique, récupéré une fois, qui décrit *a priori* où appeler le service (`service`/`port`), comment emballer les messages (`binding` : document/literal, SOAP 1.1), quelles opérations existent (`portType`) et quelles données circulent (`types`/`message`).

### Étape 3 — Envoyer des messages SOAP

**Q1. Pourquoi la requête utilise-t-elle POST, même pour lire une information ?**

Parce que SOAP (document/literal) encode l'opération et ses paramètres dans le **corps** de la requête (`<soap:Body>`), pas dans l'URL ou les en-têtes HTTP : il faut donc une méthode qui transporte un corps de requête arbitraire. `GET` n'a pas de sémantique de corps fiable (et est pensé pour des requêtes idempotentes identifiées par URL) ; `POST` est la seule méthode HTTP uniformément utilisée par les piles SOAP pour envoyer l'enveloppe, quelle que soit l'opération métier (lecture ou écriture) — HTTP n'est ici qu'un transport, la sémantique réelle (lecture/écriture) est portée par SOAP/le contrat, pas par le verbe HTTP.

**Q2. Le bloc `jeton` du Header a-t-il empêché le traitement ? Pourquoi ?**

Non : la requête `01-emprunter.xml` (avec `<bib:jeton>abc123</bib:jeton>` dans le Header, sans `mustUnderstand`) a reçu une réponse 200 normale avec un nouveau numéro d'emprunt. En SOAP, un Header que le récepteur ne connaît pas peut être **silencieusement ignoré**, sauf s'il porte `soap:mustUnderstand="1"` — ce qui n'est pas le cas ici. Le service n'a aucun `@WebParam` lié à un Header, donc Metro ignore ce bloc sans erreur (voir étape 4 pour le contraste avec `mustUnderstand="1"`).

**Q3. Que répond le service au message SOAP 1.2 ? Expliquez avec le binding lu à l'étape 2.**

Le binding lu à l'étape 2 ne déclare qu'un `<soap:binding>` SOAP 1.1 (namespace `http://schemas.xmlsoap.org/wsdl/soap/`, pas de variante `soap12:binding`) : l'endpoint n'accepte donc qu'une enveloppe dans le namespace `http://schemas.xmlsoap.org/soap/envelope/`. En envoyant `02-emprunter-12.xml` (namespace `http://www.w3.org/2003/05/soap-envelope`) avec l'en-tête `Content-Type: text/xml`, le serveur répond une **Fault `VersionMismatch`** :
```
<S:Fault><faultcode>S:VersionMismatch</faultcode>
<faultstring>Couldn't create SOAP message. Expecting Envelope in namespace
http://schemas.xmlsoap.org/soap/envelope/, but got http://www.w3.org/2003/05/soap-envelope</faultstring></S:Fault>
```
(HTTP 500). Avec le `Content-Type: application/soap+xml` propre à SOAP 1.2, le serveur répond plus tôt, avant même de lire l'enveloppe : **HTTP 415 Unsupported Media Type** (le transport HTTP léger de Metro n'accepte que `text/xml` sur cet endpoint).

**Q4. Listez les cinq erreurs du dernier message et leur correction. Certaines ne déclenchent aucune erreur côté serveur : lesquelles, et pourquoi ?**

Message fourni par l'énoncé :
```xml
<soap:envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/"
               xmlns:bib="http://supnum.mr/bibliotheque">
  <soap:Body>
    <bib:rendreLivre>
      <bib:numero>E-2026-0812</bib:numero>
    </rendreLivre>
  </soap:Body>
  <soap:Header><bib:jeton>abc123</bib:jeton></soap:Header>
</soap:Envelope>
```

> **Important — deux implémentations différentes existent chronologiquement dans ce TP.** Au moment où cette étape 3 a été testée, `EmpruntService.java` ne contenait **pas encore** la validation de `rendreLivre` ajoutée à l'étape 4 (`numeroEmprunt.startsWith("E-2026-")`). Le fichier **final livré** contient cette validation dès le départ (comme tout le code livré est un instantané final, pas un historique). Rejouer exactement les mêmes requêtes contre le code livré donne donc un résultat différent pour l'erreur 4 — documenté explicitement ci-dessous dans les deux états, chacun réellement testé (aucun résultat n'est supposé).

| # | Erreur | Correction | Déclenche une erreur serveur ? |
|---|---|---|---|
| 1 | `<soap:envelope>` (minuscule) ouvrant mais `</soap:Envelope>` (majuscule) fermant — XML non bien formé (la casse des balises est significative en XML) | `<soap:Envelope>` partout | **Oui** — testé isolément (bien formé mais garde les 3 autres erreurs) : le serveur renvoie une Fault `Client` *"unexpected XML tag...envelope"*, HTTP 500 (re-vérifié sur le code livré) |
| 2 | `<bib:rendreLivre>` ouvrant mais `</rendreLivre>` fermant sans le préfixe `bib:` — XML non bien formé (tag mismatch) | `</bib:rendreLivre>` | **Oui** — combinée à l'erreur 1 (message original complet), le document n'est pas analysable (XML non bien formé) ; **sur le code livré final**, cela produit désormais une Fault `Client` HTTP 500 complète et lisible (le parseur signale l'erreur 1 en premier : *"unexpected XML tag...envelope"*), **et non plus une connexion fermée sans réponse**. Cette dernière observation n'a été vraie que pendant le diagnostic de l'erreur d'environnement (jaxb-runtime/Executor, voir note de l'étape 4) : avant ce correctif, le serveur ne pouvait pas construire le corps de la Fault et `curl` recevait `(52) Empty reply from server` — re-vérifié sur une copie de l'état antérieur au correctif pour confirmer cette distinction |
| 3 | Mauvais espace de noms : `xmlns:bib="http://supnum.mr/bibliotheque"` au lieu de `http://supnum.mr/bib` | `xmlns:bib="http://supnum.mr/bib"` | **Oui** — testé isolément (bien formé, NS corrigé plus tard) : tant que le NS est faux, le serveur renvoie *"Cannot find dispatch method for {http://supnum.mr/bibliotheque}rendreLivre"* (Fault `Client`), HTTP 500 (re-vérifié sur le code livré). Avant le correctif d'environnement, cette même requête donnait un HTTP 500 **sans corps** (`SOAPFaultBuilder` indisponible) — re-vérifié également pour rigueur |
| 4 | Mauvais nom de paramètre : `<bib:numero>` au lieu de `<numeroEmprunt>` (et préfixé alors que les paramètres ne sont pas qualifiés) | `<numeroEmprunt>E-2026-0812</numeroEmprunt>` | **Dépend de l'implémentation, re-testé dans les deux états :**<br>— **Implémentation intermédiaire de l'étape 3** (avant l'ajout de la validation `rendreLivre`) : **silencieuse**. NS corrigé, mais nom de paramètre encore faux + Header encore après Body → **200 OK**, `penalite=0`. JAXB ignore l'élément enfant qu'il ne reconnaît pas ; `numeroEmprunt` reste `null`, et la méthode ne validait encore rien.<br>— **Code final livré** (validation de l'étape 4 déjà présente) : **n'est plus silencieuse**. La même requête échoue désormais avec une Fault `Client` : `faultstring = "Numéro d'emprunt inconnu : null"`, HTTP 500 — preuve que la validation ajoutée à l'étape 4 intercepte bien ce cas |
| 5 | `<soap:Header>` placé **après** `<soap:Body>` au lieu d'avant (Header doit précéder Body dans l'enveloppe) | Déplacer `<soap:Header>` avant `<soap:Body>` | **Non, silencieuse — dans les deux états.** Confirmée isolément avec un `numeroEmprunt` valide (`E-2026-0812`) et la validation de l'étape 4 active : **200 OK**, `penalite=0`, malgré l'ordre inversé. Metro retrouve Header et Body par leur nom qualifié (QName), pas par leur position ; c'est la seule des cinq erreurs dont le caractère silencieux ne dépend pas de l'implémentation |

**Bilan (sur le code final livré, celui qui compte pour la correction) :** une seule des cinq erreurs reste silencieuse en permanence — l'ordre Header/Body inversé (erreur 5). L'erreur 4 (nom de paramètre), silencieuse à l'étape 3, ne l'est plus une fois la validation de l'étape 4 en place : c'est précisément la preuve que cette validation fonctionne. Les trois premières erreurs (casse de balise, préfixe de fermeture manquant, mauvais espace de noms) sont bloquantes dans les deux états, soit parce que le document n'est plus un XML bien formé, soit parce que le dispatcher ne retrouve pas l'opération — seule la *forme* de l'échec (Fault lisible vs connexion fermée) a changé après le correctif d'environnement.

### Étape 4 — Provoquer et construire des Faults

> **Note d'environnement :** la première exécution de ces tests a révélé que `jaxws-rt:4.0.2` ne déclare `jaxb-impl → jaxb-runtime` qu'en portée `provided` (jamais héritée par un projet consommateur) et que le pool de threads HTTP de `com.sun.net.httpserver` ne reçoit pas toujours le classloader qui voit les dépendances Maven. Sans correctif, **aucune Fault ne pouvait se sérialiser** (connexions coupées ou 500 sans corps). Deux corrections d'environnement ont été appliquées — détaillées dans la note technique préalable en tête de ce document — : ajout explicite de `org.glassfish.jaxb:jaxb-runtime` au `pom.xml`, et un `Executor` dédié dans `Serveur.java` dont les threads héritent du bon classloader. Après correction, toutes les Faults ci-dessous se sérialisent correctement (re-confirmé par ré-exécution comparative avant/après dans l'analyse des cinq erreurs de l'étape 3).

**Q1. Pour les cas 1 et 2, quels `faultcode` et `faultstring` recevez-vous, et quel code HTTP ?**

- **Cas 1 — ISBN invalide (`978-2-10`)** : `faultcode = S:Client`, `faultstring = "ISBN mal formé : 978-2-10"`, **HTTP 500**.
- **Cas 2 — livre déjà emprunté (`978-2-10-074444-4`)** : `faultcode = S:Server`, `faultstring = "Livre indisponible"`, **HTTP 500**.

Les deux cas renvoient le même code HTTP 500 : en SOAP 1.1, HTTP n'est qu'un transport — la distinction Client/Server se lit dans le `faultcode` SOAP, pas dans le code HTTP (contrairement à une API REST où l'on attendrait 400 vs 500).

**Q2. Dans le cas 2, que contient `<detail>` ? Où sa structure est-elle décrite dans le WSDL ?**

```xml
<detail><ns2:LivreIndisponible xmlns:ns2="http://supnum.mr/bib">
  <isbn>978-2-10-074444-4</isbn>
  <retourPrevu>2026-12-02</retourPrevu>
</ns2:LivreIndisponible></detail>
```
`<detail>` contient le bean `LivreIndisponibleInfo` sérialisé par JAXB (champs `isbn` et `retourPrevu`). Sa structure est décrite dans le WSDL par le `<wsdl:fault name="LivreIndisponibleException">` de l'opération `emprunterLivre`, qui référence le message `LivreIndisponibleException` → élément `tns:LivreIndisponible`, lui-même défini dans le XSD (`<xsd:complexType name="livreIndisponibleInfo">` avec `isbn` et `retourPrevu`, tous deux `xsd:string` en code-first — voir l'étape 2).

**Q3. Quel code JAXWS a-t-il choisi pour le livre indisponible ? Est-ce une erreur du client ou du serveur ? Qu'auriez-vous choisi ?**

JAX-WS a choisi `faultcode = Server` (`faultstring` reste le message métier "Livre indisponible"). C'est discutable : la cause réelle est un **état métier** (le livre est déjà sorti), pas une panne du serveur — on pourrait arguer qu'un `Client` (l'appelant demande un livre indisponible, ce n'est pas « sa faute » au sens SOAP mais ce n'est pas non plus un bug serveur) serait plus proche de l'intention WS-I. En pratique, JAX-WS mappe par défaut toute exception **déclarée via `@WebFault`** (une exception *checked*, donc métier) sur `Server`, car le framework ne sait pas distinguer automatiquement une erreur de requête d'une erreur d'état interne : seules les erreurs qu'on lève nous-même explicitement en `Client` (via `SOAPFaultException` avec le QName `Client`, comme le fait `faultClient()`) portent ce code. **Choix personnel** : j'aurais plutôt laissé `Server` par défaut ici aussi, car la disponibilité du livre est un état du système au moment de l'appel (pas une erreur de forme de la requête comme un ISBN mal formé) — mais le codé explicitement en `Client` serait également défendable si l'on considère que l'appelant « n'avait qu'à vérifier » la disponibilité avant d'emprunter.

**Q4. Que se passe-t-il avec `mustUnderstand="1"` ? Et avec `"0"` ?**

- `mustUnderstand="1"` → Fault **`MustUnderstand`** : `faultstring = "MustUnderstand headers:[{http://supnum.mr/bib}jeton] are not understood"`. Le service, qui ne déclare aucun traitement pour ce Header, est obligé par la norme SOAP de rejeter le message plutôt que de l'ignorer silencieusement.
- `mustUnderstand="0"` (ou absent) → le Header est ignoré et le message est traité normalement (**HTTP 200**, emprunt effectué) — testé avec un ISBN non déjà emprunté pour isoler l'effet du Header.

**Q5. Quelle différence voyez-vous entre votre Fault 1.2 (`03-fault-sender-12.xml`) et les Faults 1.1 reçues ?**

En SOAP 1.1, la Fault est un `<soap:Fault>` à plat avec des éléments simples `<faultcode>` (une `QName`, ex. `S:Client`), `<faultstring>` (texte brut, pas de langue) et `<detail>` optionnel. En SOAP 1.2 (`03-fault-sender-12.xml`), la structure est **imbriquée et typée** : `<soap:Code><soap:Value>soap:Sender</soap:Value></soap:Code>` (le code devient un élément structuré, pas un simple texte), `<soap:Reason><soap:Text xml:lang="fr">...</soap:Text></soap:Reason>` (le message humain supporte explicitement la **localisation**, plusieurs `<soap:Text>` pour plusieurs langues étant possibles), et `<soap:Detail>` (capitalisé, au lieu de `<detail>`). Les noms `Client`/`Server` de 1.1 deviennent aussi `Sender`/`Receiver` en 1.2.

## Partie B — Contract-first

### Étape 5 — Générer le code depuis le WSDL

**Q1. Dans ce WSDL, retrouvez l'opération, son message de fault et l'action SOAP à envoyer.**

Opération : `emprunterLivre` (seule opération du `portType EmpruntPortType`). Message de fault : `LivreIndisponibleFault` (`<wsdl:fault name="LivreIndisponible" message="bib:LivreIndisponibleFault">`, élément `bib:LivreIndisponible`). Action SOAP à envoyer : `soapAction="http://supnum.mr/bib/emprunterLivre"` (déclarée dans le `binding`, à placer dans l'en-tête HTTP `SOAPAction`).

**Q2. Quelle est la signature de `emprunterLivre` dans `EmpruntPortType` ?**

```java
@WebMethod(action = "http://supnum.mr/bib/emprunterLivre")
@WebResult(name = "emprunt", targetNamespace = "http://supnum.mr/bib")
public EmpruntType emprunterLivre(String idAdherent, String isbn) throws LivreIndisponibleFault;
```
(générée dans `target/generated-sources/wsimport/mr/supnum/bib/contrat/EmpruntPortType.java` — nom de classe identique à celui attendu par l'énoncé).

**Q3. Quel type Java a été généré pour `dateRetour` ? Comparez avec la partie A.**

`javax.xml.datatype.XMLGregorianCalendar` (champ de `EmpruntType.java`, annoté `@XmlSchemaType(name = "date")`), parce que le contrat déclare `dateRetour` en `xs:date` — JAXB mappe systématiquement `xs:date` sur `XMLGregorianCalendar`, jamais sur `String` ou `LocalDate`. **En partie A (code-first)**, `dateRetour` était un simple `String` dans `Emprunt.java`, et le XSD généré en conséquence était `xs:string` (pas `xs:date`) : le type du contrat suivait passivement le type du champ Java. Ici c'est l'inverse — le contrat impose `xs:date`, donc l'outil génère un type Java plus riche et plus contraint (`XMLGregorianCalendar`), ce qui oblige à écrire une petite fonction de conversion (`DatatypeFactory`) dans l'implémentation.

**Q4. Quel est le nom de la classe d'exception générée, et d'où vient ce nom dans le WSDL ?**

`LivreIndisponibleFault`. Ce nom vient du `<wsdl:message name="LivreIndisponibleFault">` déclaré dans le contrat (`<wsdl:part name="fault" element="bib:LivreIndisponible"/>`) — `wsimport` nomme la classe Java d'exception générée d'après le **nom du message WSDL**, pas d'après le nom de l'élément XML (`LivreIndisponible`, lui, devient le nom du bean de détail via `@WebFault(name = "LivreIndisponible", ...)` sur cette même classe généré, et le nom de la classe JAXB du bean de détail lui-même, `LivreIndisponible.java`).

### Étape 6 — Implémenter et comparer

**Q1. Le WSDL publié est-il celui que vous avez fourni ? Contient-il le motif de l'ISBN et le type `xs:date` ?**

**Oui, avec `wsdlLocation`** : le WSDL récupéré sur `?wsdl` est structurellement identique (mêmes noms `EmpruntPortType`, `EmpruntSoapBinding`, mêmes messages, même ordre) à celui écrit à la main dans `src/main/resources/wsdl/EmpruntService.wsdl` — Metro republie le fichier fourni au lieu d'en régénérer un depuis les annotations. Il contient donc bien le `xs:pattern` de `IsbnType` et le type `xs:date` de `dateRetour`/`retourPrevu`.

**Non, sans `wsdlLocation`** (testé en retirant l'attribut — voir Q4) : Metro régénère entièrement le WSDL depuis l'interface Java, et **le motif de l'ISBN disparaît** (`isbn` redevient `xs:string` nu) ; seul `xs:date` persiste (parce qu'il est capturé dans le type Java généré `XMLGregorianCalendar` via `@XmlSchemaType`, contrairement au pattern qui n'existe que dans le fichier WSDL source).

**Q2. Avez-vous dû modifier `01-emprunter.xml` pour qu'il fonctionne ici ? Pourquoi ?**

Oui. Le contrat écrit à la main déclare `elementFormDefault="qualified"` dans son schéma (contrairement au schéma généré de la partie A, implicitement non qualifié) : les éléments `idAdherent` et `isbn` doivent donc être préfixés par le namespace cible (`<bib:idAdherent>`, `<bib:isbn>`) pour être reconnus. Il a aussi fallu fournir l'en-tête `SOAPAction: "http://supnum.mr/bib/emprunterLivre"` exact déclaré dans le binding (au lieu de `""` utilisé en partie A).

**Comportement observé en envoyant le fichier `01-emprunter.xml` original (paramètres non qualifiés), re-testé dans les deux états de `EmpruntServiceImpl` :**
- **Avant l'ajout de `@SchemaValidation`** (implémentation de l'étape 6 avant son dernier point) : les éléments non qualifiés ne sont liés à aucune propriété du bean généré, donc `isbn` reste `null` à l'entrée de la méthode ; `isbn.equals("978-2-10-074444-4")` lève alors une `NullPointerException`, remontée en Fault `Server` : `faultstring = "Cannot invoke \"String.equals(Object)\" because \"isbn\" is null"`, HTTP 500.
- **Sur le code final livré** (où `@SchemaValidation` est actif dès le démarrage) : la validation XSD intercepte la requête **avant** d'atteindre le code métier, donc la `NullPointerException` ne se produit plus. La Fault observée est désormais : `faultcode = S:Client`, `faultstring = "org.xml.sax.SAXParseException; cvc-complex-type.2.4.a: Invalid content was found starting with element 'idAdherent'. One of '{\"http://supnum.mr/bib\":idAdherent}' is expected."`, HTTP 500.

Avec les paramètres qualifiés (`<bib:idAdherent>`/`<bib:isbn>`), la requête réussit dans les deux états (**200 OK**).

**Q3. Sans `@SchemaValidation`, l'ISBN `978-2-10` est-il refusé ? Et avec ? Quelle Fault obtenez-vous ?**

**Sans** `@SchemaValidation` (re-testé sur une copie de l'implémentation sans l'annotation) : l'ISBN invalide `978-2-10` est **accepté** (HTTP 200, emprunt effectué) — `wsimport` ne traduit pas la restriction `IsbnType` en validation Java à l'exécution ; le type généré est un simple `String`, et `EmpruntServiceImpl` (contrairement à `EmpruntService` de la partie A) ne fait aucune vérification de format par elle-même. Le contrat déclare la règle, mais rien ne l'applique.

**Avec** `@com.sun.xml.ws.developer.SchemaValidation` sur la classe : la même requête est **rejetée**, avec une Fault `Client` dont le `faultstring` est :
```
org.xml.sax.SAXParseException; cvc-pattern-valid: Value '978-2-10' is not facet-valid with
respect to pattern '97[89]-\d-\d{2,5}-\d{2,6}-\d' for type 'IsbnType'.
```
L'annotation active une validation XSD complète du message entrant par rapport au schéma du contrat avant même d'atteindre le code métier — c'est la seule façon, en contract-first, de faire respecter une contrainte purement déclarative comme `xs:pattern`.

**Q4. Retirez `wsdlLocation` de l'annotation et redémarrez : qu'est-ce qui change dans le WSDL publié ?**

Changement majeur : Metro **régénère** entièrement le WSDL depuis la seule réflexion sur l'interface/l'implémentation (comme en code-first), au lieu de republier le fichier `.wsdl` fourni. Conséquences observées :
- Le `<types>` régénéré perd la restriction `IsbnType` : `isbn` redevient `xs:string` nu (**le motif de l'ISBN disparaît du contrat publié**, bien qu'il reste écrit dans le fichier source `.wsdl` sur disque — simplement, il n'est plus republié).
- `xs:date` pour `dateRetour`/`retourPrevu` est conservé (car porté par l'annotation JAXB `@XmlSchemaType` sur le type Java généré, indépendante du fichier WSDL).
- Les noms choisis à la main changent pour des noms par défaut : `binding` passe de `EmpruntSoapBinding` à `EmpruntPortBinding`, et le message de fault de `LivreIndisponible` à `LivreIndisponibleFault` (nom dérivé de la classe Java, plus du WSDL source).

Conclusion pratique : `wsdlLocation` n'est pas un simple détail de configuration — c'est lui qui garantit que le contrat **réellement publié** est bien celui qu'on a écrit et fait relire/valider, plutôt qu'une reconstruction approximative perdant les contraintes qui n'ont pas d'équivalent en Java (comme un `xs:pattern`). L'implémentation finale conservée pour la livraison restaure `wsdlLocation` et garde `@SchemaValidation`.

## Conclusion — Choisir et rendre

**Q1. Citez deux différences que vous avez observées entre le WSDL de la partie A et celui de la partie B.**

1. **Contrôle du contrat / règles de validation** : le WSDL de la partie A (code-first) expose `isbn` et `dateRetour` en `xs:string` nus, sans aucune trace de la règle de format de l'ISBN (le `Pattern` Java reste caché dans le code) ; celui de la partie B (contract-first) déclare `IsbnType` avec son `xs:pattern` et utilise `xs:date` pour les dates — le contrat est auto-documenté et plus strict.
2. **Qualification des éléments et noms** : la partie A génère un schéma avec `elementFormDefault` implicite (non qualifié — `idAdherent`/`isbn` sans préfixe dans le message), un `portType` nommé d'après la classe Java (`EmpruntService`) et un `binding` nommé `EmpruntPortBinding` ; la partie B impose `elementFormDefault="qualified"` (les paramètres doivent être préfixés), et les noms (`EmpruntPortType`, `EmpruntSoapBinding`) sont ceux choisis à la main dans le contrat, indépendants de tout nom de classe Java. Autrement dit : en code-first, le contrat *suit* le code ; en contract-first, c'est le code qui *suit* le contrat (le WSDL publié est, à l'octet près, celui qu'on a fourni).

**Q2. Si on renomme le champ `dateRetour` dans le code, quel projet voit son contrat changer ? Quel risque pour les clients ?**

En **code-first** (partie A), renommer le champ Java `dateRetour` dans `Emprunt.java` change *directement* le WSDL généré (l'élément XML prendrait le nouveau nom au prochain redémarrage) : le contrat suit silencieusement le code, sans qu'on s'en rende compte en relisant seulement les sources Java. **Risque pour les clients** : toute application déjà construite sur le contrat précédent (client généré par `wsimport`, mapping XML codé en dur, etc.) cesse de fonctionner au prochain déploiement, sans avertissement préalable — c'est une rupture de compatibilité silencieuse et non versionnée.

En **contract-first** (partie B), renommer le champ Java correspondant (`EmpruntType.dateRetour`, généré depuis le WSDL) n'a **aucun effet sur le contrat** : le WSDL est un fichier source indépendant (`EmpruntService.wsdl`), toujours republié tel quel ; seul le nom de la propriété Java générée par `wsimport` changerait si on modifiait le WSDL *lui-même*, et ce changement serait alors visible et volontaire (on modifie le fichier `.wsdl`, on relance `generate-sources`, on voit le diff). Le risque pour les clients existe toujours en cas de modification du contrat, mais il est *explicite* et maîtrisé plutôt que subi.

**Q3. Quelle approche choisiriez-vous pour le service Paiement, appelé par Scolarité (.NET), Bibliothèque (PHP) et le portail Node.js ? Et pour un prototype interne de deux jours ?**

Pour le service **Paiement partagé par trois équipes/langages différents**, je choisirais résolument le **contract-first** : le WSDL devient le contrat stable, versionné et indépendant de toute implémentation — .NET, PHP et Node.js peuvent chacun générer leur client depuis le même fichier `.wsdl` sans dépendre des détails internes (noms de champs Java, framework utilisé côté serveur). C'est exactement le scénario où code-first est dangereux : un simple renommage de champ côté serveur Java romprait silencieusement les trois clients à la fois (voir Q2). Contract-first impose aussi une discipline utile ici : toute évolution du contrat passe par une modification explicite et relue du `.wsdl`, ce qui facilite la coordination entre équipes qui ne partagent pas le même langage.

Pour un **prototype interne de deux jours** (un seul consommateur, pas d'enjeu de compatibilité à long terme), je choisirais le **code-first** : on itère plus vite en partant directement des classes Java annotées, sans avoir à écrire XSD/WSDL à la main, et JAX-WS régénère le contrat automatiquement à chaque changement — exactement ce qu'il faut pour explorer rapidement. La seule condition posée par l'énoncé — « à condition de figer le WSDL avant la production » — reste vraie : si ce prototype doit un jour être exposé à d'autres équipes ou passer en production, il faudra à ce moment geler le WSDL généré (le sauvegarder, basculer en contract-first ou au moins cesser de modifier librement les classes porteuses du contrat) pour éviter le risque de rupture silencieuse décrit en Q2.

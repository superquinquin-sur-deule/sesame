# Sésame

Vérification du statut d'un coopérateur pour le PC d'accueil SuperQuinquin.

Recherche par prénom, nom ou n° de coopérateur, et affichage d'une fiche
détaillée avec le statut (à jour / en alerte / suspendu / désinscrit),
le prochain créneau de bénévolat et le binôme.
Offre la possibilité de prendre la photo du coopérateur et de l'enregistrer dans Odoo

## Stack

- Backend : Quarkus (Java 21) — JSON-RPC vers Odoo
- Frontend : Vite + Vue 3 (TypeScript), servi par Quinoa
- Client API typé : orval, généré depuis l'OpenAPI Quarkus

## Configuration

Renseigner un fichier `.env` à la racine ou passer les variables d'environnement directement

```
ODOO_URL=https://...
ODOO_DATABASE=...
ODOO_LOGIN=...
ODOO_PASSWORD=...
```

## Lancer en dev

```sh
./mvnw quarkus:dev
```

Application sur <http://localhost:8080>, dev UI Quarkus sur `/q/dev/`.

## Packager

```sh
./mvnw package
java -jar target/quarkus-app/quarkus-run.jar
```

## Tests

```sh
./mvnw test
```

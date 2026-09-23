#!/bin/bash
# Runs once, automatically, on first startup of the ecommerce-db container
# (official postgres image convention: anything in /docker-entrypoint-initdb.d/
# executes against the default database on first init). Creates one database +
# one matching role per e-commerce service, giving each service its own schema -
# real per-service data isolation, not a shared table space.
set -e

create_db_and_user() {
  local db="$1"
  local user="$2"
  local pass="$3"
  psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" <<-SQL
    CREATE USER ${user} WITH PASSWORD '${pass}';
    CREATE DATABASE ${db} OWNER ${user};
    GRANT ALL PRIVILEGES ON DATABASE ${db} TO ${user};
SQL
}

create_db_and_user "catalog_db"   "catalog"   "${CATALOG_DB_PASSWORD:-catalog}"
create_db_and_user "order_db"     "order_svc" "${ORDER_DB_PASSWORD:-order_svc}"
create_db_and_user "payment_db"   "payment"   "${PAYMENT_DB_PASSWORD:-payment}"
create_db_and_user "inventory_db" "inventory" "${INVENTORY_DB_PASSWORD:-inventory}"

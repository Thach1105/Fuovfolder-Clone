-- Align uploaded_files.checksum_sha256 with JPA String mapping (varchar), not PostgreSQL char(64).

alter table uploaded_files
    alter column checksum_sha256 type varchar(64) using trim(checksum_sha256);

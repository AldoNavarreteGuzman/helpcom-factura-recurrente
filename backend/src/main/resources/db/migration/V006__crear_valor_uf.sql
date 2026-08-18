-- Tabla global (sin empresa_id): el valor de la UF es nacional, no depende de la empresa.
CREATE TABLE valor_uf (
  fecha         DATE PRIMARY KEY,
  valor         NUMERIC(12,4) NOT NULL,
  fuente        VARCHAR(40)   NOT NULL,
  registrado_en TIMESTAMPTZ   NOT NULL DEFAULT now(),
  CONSTRAINT ck_valor_uf_valor CHECK (valor > 0)
);

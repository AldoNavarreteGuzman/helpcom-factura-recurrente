-- TODO: reemplazar el RUT placeholder por el RUT real de Helpcom Ltda. antes de producción.
INSERT INTO empresa (rut, razon_social, activa, creado_por)
VALUES ('XX.XXX.XXX-X', 'Helpcom Ltda.', TRUE, 'sistema');

INSERT INTO parametro_sistema (empresa_id, clave, valor, descripcion, creado_por)
VALUES ((SELECT id FROM empresa WHERE razon_social = 'Helpcom Ltda.'),
        'tasa_iva', '0.19', 'Tasa de IVA vigente', 'sistema');

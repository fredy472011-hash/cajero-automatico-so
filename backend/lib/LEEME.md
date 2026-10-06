# Dependencias

El único `.jar` que necesita el proyecto es el driver JDBC de MySQL.
No está en el repositorio porque pesa unos 2,5 MB y se descarga en un paso.

## Descargar

https://dev.mysql.com/downloads/connector/j/  → "Platform Independent"

Descomprimir y copiar `mysql-connector-j-9.4.0.jar` en esta carpeta.

Si se usa otra versión, cambiar el nombre en los comandos del README.

## Por qué hace falta

`java.sql` viene en el JDK, pero es solo la **interfaz**. La implementación
concreta para MySQL la trae este `.jar`: cuando `DriverManager.getConnection()`
recibe una URL que arranca con `jdbc:mysql:`, busca en el classpath quién sabe
hablar ese protocolo.

Sin el `.jar`, el error es:

```
No suitable driver found for jdbc:mysql://localhost:3306/cajero
```

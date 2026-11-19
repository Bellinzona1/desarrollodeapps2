<#
    =========================================================================
     MediConecta - configuracion completa de WildFly en un solo paso
    =========================================================================

     Deja el servidor listo para desplegar mediconecta.war:

       1. descarga el driver JDBC de PostgreSQL desde Maven Central;
       2. lo instala como modulo de WildFly (org.postgresql);
       3. crea el datasource java:/jdbc/MediConectaDS;
       4. prepara la seguridad JWT (integrated-jaspi=false) y recarga.

     Los usuarios de demostracion ya NO se crean en WildFly: desde la
     Entrega 2 viven en la tabla "usuarios" (contrasena con hash PBKDF2) y los
     carga la aplicacion al desplegar.

     Uso (PowerShell, desde cualquier carpeta):

         .\configurar-wildfly.ps1 -WildFly "C:\wildfly-36.0.1.Final"

     Los pasos 1 y 2 necesitan el servidor APAGADO.
     Los pasos 3 y 4 necesitan el servidor ENCENDIDO, con el perfil
     standalone-full.xml (bin\standalone.bat -c standalone-full.xml).

     Las credenciales de PostgreSQL se pueden pasar por parametro; el valor por
     omision es el del contenedor Docker de scripts/db/levantar-postgres.ps1.
     No hay ninguna contrasena de base de datos dentro del codigo Java.
    =========================================================================
#>

param(
    [Parameter(Mandatory = $true)]
    [string] $WildFly,

    [string] $VersionDriver = "42.7.4",
    [string] $UsuarioBase   = "mediconecta",
    [string] $ClaveBase     = "mediconecta",
    [string] $UrlBase       = "jdbc:postgresql://localhost:5432/mediconecta",
    [switch] $SoloSeguridad,
    [switch] $SoloDatasource
)

$ErrorActionPreference = "Stop"

function Paso($texto) {
    Write-Host ""
    Write-Host "==> $texto" -ForegroundColor Cyan
}

if (-not (Test-Path $WildFly)) {
    throw "No existe la carpeta de WildFly: $WildFly"
}

$cli = Join-Path $WildFly "bin\jboss-cli.bat"

# ---------------------------------------------------------------- 1 y 2 -----
if (-not $SoloSeguridad -and -not $SoloDatasource) {

    Paso "Descargando el driver de PostgreSQL $VersionDriver"

    $destino = Join-Path $env:TEMP "postgresql-$VersionDriver.jar"
    if (-not (Test-Path $destino)) {
        $url = "https://repo1.maven.org/maven2/org/postgresql/postgresql/$VersionDriver/postgresql-$VersionDriver.jar"
        Invoke-WebRequest -Uri $url -OutFile $destino -UseBasicParsing
    }
    Write-Host "   Driver en $destino"

    Paso "Instalando el modulo org.postgresql en WildFly (el servidor debe estar apagado)"

    $modulo = Join-Path $WildFly "modules\org\postgresql\main"
    if (Test-Path (Join-Path $modulo "module.xml")) {
        Write-Host "   El modulo ya estaba instalado."
    } else {
        & $cli "--command=module add --name=org.postgresql --resources=$destino --dependencies=jakarta.transaction.api,java.sql,java.logging"
        if ($LASTEXITCODE -ne 0) { throw "Fallo la instalacion del modulo." }
        Write-Host "   Modulo instalado en $modulo"
    }
}

# ------------------------------------------------------------------- 3 ------
if (-not $SoloSeguridad) {

    Paso "Creando el datasource java:/jdbc/MediConectaDS (el servidor debe estar ENCENDIDO)"

    $script = Join-Path $PSScriptRoot "configurar-datasource.cli"

    # --resolve-parameter-values hace que el CLI reemplace ${mediconecta.db.*}
    # con los -D de abajo ANTES de mandar la operacion. Sin esa opcion el
    # servidor guarda la expresion tal cual y siempre usa el valor por omision.
    & $cli --connect --resolve-parameter-values `
        "-Dmediconecta.db.url=$UrlBase" `
        "-Dmediconecta.db.usuario=$UsuarioBase" `
        "-Dmediconecta.db.clave=$ClaveBase" `
        "--file=$script"

    if ($LASTEXITCODE -ne 0) {
        throw "No se pudo crear el datasource. Verifica que WildFly este levantado y que PostgreSQL responda."
    }
}

# ------------------------------------------------------------------- 4 ------
Paso "Preparando la seguridad JWT (integrated-jaspi=false)"

& $cli --connect "--file=$(Join-Path $PSScriptRoot 'configurar-seguridad.cli')"
if ($LASTEXITCODE -ne 0) {
    throw "No se pudo configurar la seguridad. Verifica que WildFly este levantado."
}

# ------------------------------------------------------------------- 5 ------
Paso "Configurando la mensajeria (journal persistente, reintentos y DLQ)"

& $cli --connect "--file=$(Join-Path $PSScriptRoot 'configurar-mensajeria.cli')"
if ($LASTEXITCODE -ne 0) {
    throw "No se pudo configurar la mensajeria. El servidor tiene que estar levantado con -c standalone-full.xml."
}

Write-Host ""
Write-Host "WildFly configurado. Ya se puede copiar target\mediconecta.war a standalone\deployments\." -ForegroundColor Green

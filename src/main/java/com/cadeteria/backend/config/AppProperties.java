package com.cadeteria.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/** Configuracion de la app bajo el prefijo `app.*` (ver application.yml). */
@ConfigurationProperties(prefix = "app")
public class AppProperties {

    private Jwt jwt = new Jwt();
    private Admin admin = new Admin();
    private Cors cors = new Cors();
    private Seed seed = new Seed();
    private Demo demo = new Demo();
    private Sms sms = new Sms();
    private Whatsapp whatsapp = new Whatsapp();
    private Maps maps = new Maps();
    private Fcm fcm = new Fcm();
    private Mail mail = new Mail();
    private Cloudinary cloudinary = new Cloudinary();
    private Verificacion verificacion = new Verificacion();
    private String frontBaseUrl = "http://localhost:4200";

    public String getFrontBaseUrl() {
        return frontBaseUrl;
    }

    public void setFrontBaseUrl(String frontBaseUrl) {
        this.frontBaseUrl = frontBaseUrl;
    }

    public Jwt getJwt() {
        return jwt;
    }

    public void setJwt(Jwt jwt) {
        this.jwt = jwt;
    }

    public Admin getAdmin() {
        return admin;
    }

    public void setAdmin(Admin admin) {
        this.admin = admin;
    }

    public Cors getCors() {
        return cors;
    }

    public void setCors(Cors cors) {
        this.cors = cors;
    }

    public Seed getSeed() {
        return seed;
    }

    public void setSeed(Seed seed) {
        this.seed = seed;
    }

    public Demo getDemo() {
        return demo;
    }

    public void setDemo(Demo demo) {
        this.demo = demo;
    }

    public Sms getSms() {
        return sms;
    }

    public void setSms(Sms sms) {
        this.sms = sms;
    }

    public Whatsapp getWhatsapp() {
        return whatsapp;
    }

    public void setWhatsapp(Whatsapp whatsapp) {
        this.whatsapp = whatsapp;
    }

    public Maps getMaps() {
        return maps;
    }

    public void setMaps(Maps maps) {
        this.maps = maps;
    }

    public Fcm getFcm() {
        return fcm;
    }

    public void setFcm(Fcm fcm) {
        this.fcm = fcm;
    }

    public Mail getMail() {
        return mail;
    }

    public void setMail(Mail mail) {
        this.mail = mail;
    }

    public Verificacion getVerificacion() {
        return verificacion;
    }

    public void setVerificacion(Verificacion verificacion) {
        this.verificacion = verificacion;
    }

    public Cloudinary getCloudinary() {
        return cloudinary;
    }

    public void setCloudinary(Cloudinary cloudinary) {
        this.cloudinary = cloudinary;
    }

    public static class Jwt {
        private String secret;
        private long expirationMinutes = 720;

        public String getSecret() {
            return secret;
        }

        public void setSecret(String secret) {
            this.secret = secret;
        }

        public long getExpirationMinutes() {
            return expirationMinutes;
        }

        public void setExpirationMinutes(long expirationMinutes) {
            this.expirationMinutes = expirationMinutes;
        }
    }

    /** Credenciales del admin INICIAL: solo siembran el primer usuario si la tabla admin esta vacia. */
    public static class Admin {
        private String username = "admin";
        private String password = "cambiar123";

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }
    }

    public static class Cors {
        private List<String> allowedOrigins = List.of("http://localhost:4200");

        public List<String> getAllowedOrigins() {
            return allowedOrigins;
        }

        public void setAllowedOrigins(List<String> allowedOrigins) {
            this.allowedOrigins = allowedOrigins;
        }
    }

    public static class Seed {
        private boolean enabled = true;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }

    /** Pedidos de ejemplo para el dashboard/Métricas (ver DemoPedidoSeeder) — apagar en producción. */
    public static class Demo {
        private boolean enabled = true;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }

    /** URL base del android-sms-gateway (diseno-tecnico.md sección 6/8). */
    public static class Sms {
        private String gatewayUrl = "";
        private String gatewayUser = "";
        private String gatewayPassword = "";
        private boolean enabled = false;

        public String getGatewayUrl() {
            return gatewayUrl;
        }

        public void setGatewayUrl(String gatewayUrl) {
            this.gatewayUrl = gatewayUrl;
        }

        public String getGatewayUser() {
            return gatewayUser;
        }

        public void setGatewayUser(String gatewayUser) {
            this.gatewayUser = gatewayUser;
        }

        public String getGatewayPassword() {
            return gatewayPassword;
        }

        public void setGatewayPassword(String gatewayPassword) {
            this.gatewayPassword = gatewayPassword;
        }

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }

    /**
     * Gateway propio de WhatsApp (Baileys + chips descartables, corre en una PC local,
     * se conecta al backend por STOMP). El token evita que cualquiera que encuentre el
     * endpoint /ws pueda suscribirse a /topic/whatsapp/comandos (vería teléfonos y
     * textos de clientes) o mandar acks falsos a /app/whatsapp/ack.
     */
    public static class Whatsapp {
        private String gatewayToken = "";
        /**
         * Solo desarrollo (spec-antiabuso §6): los códigos de verificación no se mandan, se
         * guardan como mensaje ENVIADO con chip "SIMULADO" y se leen en la pestaña "Mensajes
         * enviados" del panel de WhatsApp. Nunca prenderlo en producción: el sistema simularía
         * mandar códigos que nadie recibe.
         */
        private boolean modoSimulado = false;

        public boolean isModoSimulado() {
            return modoSimulado;
        }

        public void setModoSimulado(boolean modoSimulado) {
            this.modoSimulado = modoSimulado;
        }

        public String getGatewayToken() {
            return gatewayToken;
        }

        public void setGatewayToken(String gatewayToken) {
            this.gatewayToken = gatewayToken;
        }
    }

    /**
     * Nominatim (reverse geocoding) y OpenRouteService (ruta), ambos gratuitos (spec 5.1/5.7).
     * Solo las URLs (genéricas, no cambian por instalación) — las API keys de ruteo
     * (OpenRouteService, GraphHopper) se cargan desde Configuración/el panel, no acá, porque
     * cada cliente tiene las suyas (ver RutaService).
     */
    public static class Maps {
        private String nominatimUrl = "https://nominatim.openstreetmap.org";
        private String openRouteServiceUrl = "https://api.heigit.org/openrouteservice";
        /** Geoapify para el proxy de direcciones de "/pedir" (mejora 2026-09-17) — ver GeocodingProxyService. Vacío = solo Nominatim. */
        private String geoapifyKey = "";
        /**
         * LocationIQ para el mismo proxy (spec-geocoding-cache.md, Fase 1) — antes solo estaba en
         * el front (`geocoding.service.ts`), expuesta en el bundle. Default: la misma key gratuita
         * que ya usaba el front, movida acá. Vacío = no se consulta.
         */
        private String locationIqKey = "pk.117f16f1d630ef90773656ffa29db89f";

        public String getNominatimUrl() {
            return nominatimUrl;
        }

        public void setNominatimUrl(String nominatimUrl) {
            this.nominatimUrl = nominatimUrl;
        }

        public String getOpenRouteServiceUrl() {
            return openRouteServiceUrl;
        }

        public void setOpenRouteServiceUrl(String openRouteServiceUrl) {
            this.openRouteServiceUrl = openRouteServiceUrl;
        }

        public String getGeoapifyKey() {
            return geoapifyKey;
        }

        public void setGeoapifyKey(String geoapifyKey) {
            this.geoapifyKey = geoapifyKey;
        }

        public String getLocationIqKey() {
            return locationIqKey;
        }

        public void setLocationIqKey(String locationIqKey) {
            this.locationIqKey = locationIqKey;
        }
    }

    /** Push a la app de cadetes via Firebase Cloud Messaging (spec: mismo esquema que ya usan). */
    public static class Fcm {
        /** Ruta al JSON de credenciales de la cuenta de servicio de Firebase. Vacio = push deshabilitado. */
        private String credencialesPath = "";

        public String getCredencialesPath() {
            return credencialesPath;
        }

        public void setCredencialesPath(String credencialesPath) {
            this.credencialesPath = credencialesPath;
        }
    }

    /** SMTP para el mail de alta de cadete (ronda 7). Host vacío = envío deshabilitado (se loguea, no rompe el alta). */
    public static class Mail {
        private String host = "";
        private int port = 587;
        private String username = "";
        private String password = "";
        private String from = "no-reply@cadeteria.local";

        public String getHost() {
            return host;
        }

        public void setHost(String host) {
            this.host = host;
        }

        public int getPort() {
            return port;
        }

        public void setPort(int port) {
            this.port = port;
        }

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }

        public String getFrom() {
            return from;
        }

        public void setFrom(String from) {
            this.from = from;
        }
    }

    /**
     * Credenciales de la Admin API de Cloudinary, para poder borrar archivos de verdad
     * (mejora 2026-09-23) — el cloud_name/upload_preset ya vive en Configuración (se usa
     * para el upload sin firmar desde el navegador), pero api_key/api_secret son
     * sensibles y no se guardan en la base. api_key o api_secret vacío = borrado
     * deshabilitado (la limpieza de referencias en la base sigue andando igual, ver
     * RetencionDatosService/CloudinaryService).
     */
    public static class Cloudinary {
        private String apiKey = "";
        private String apiSecret = "";

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }

        public String getApiSecret() {
            return apiSecret;
        }

        public void setApiSecret(String apiSecret) {
            this.apiSecret = apiSecret;
        }
    }

    /**
     * Por dónde sale el código de verificación de "/pedir" (spec-antiabuso §4 Fase 2):
     * WHATSAPP | SMS | AUTO. AUTO = WhatsApp si el gateway está conectado, si no SMS, y si
     * tampoco hay SMS la solicitud entra marcada "sin verificar" para que la valide el admin.
     */
    public static class Verificacion {
        private String transporte = "AUTO";

        public String getTransporte() {
            return transporte;
        }

        public void setTransporte(String transporte) {
            this.transporte = transporte;
        }
    }
}

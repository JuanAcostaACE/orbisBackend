package com.orbis.api.vision.yolo;

/**
 * Las 80 clases de COCO en el orden de salida del modelo YOLOX-Nano, con su nombre en español.
 * Solo contiene clases COCO reales: no existen "puerta" ni "escalera" en este vocabulario.
 */
public enum ClaseCoco {
    PERSONA("persona", "personas", true),
    BICICLETA("bicicleta", "bicicletas", true),
    COCHE("coche", "coches", false),
    MOTOCICLETA("motocicleta", "motocicletas", true),
    AVION("avión", "aviones", false),
    AUTOBUS("autobús", "autobuses", false),
    TREN("tren", "trenes", false),
    CAMION("camión", "camiones", false),
    BARCO("barco", "barcos", false),
    SEMAFORO("semáforo", "semáforos", false),
    BOCA_INCENDIO("boca de incendio", "bocas de incendio", true),
    SENAL_STOP("señal de stop", "señales de stop", true),
    PARQUIMETRO("parquímetro", "parquímetros", false),
    BANCO("banco", "bancos", false),
    PAJARO("pájaro", "pájaros", false),
    GATO("gato", "gatos", false),
    PERRO("perro", "perros", false),
    CABALLO("caballo", "caballos", false),
    OVEJA("oveja", "ovejas", true),
    VACA("vaca", "vacas", true),
    ELEFANTE("elefante", "elefantes", false),
    OSO("oso", "osos", false),
    CEBRA("cebra", "cebras", true),
    JIRAFA("jirafa", "jirafas", true),
    MOCHILA("mochila", "mochilas", true),
    PARAGUAS("paraguas", "paraguas", false),
    BOLSO("bolso", "bolsos", false),
    CORBATA("corbata", "corbatas", true),
    MALETA("maleta", "maletas", true),
    FRISBEE("frisbee", "frisbees", false),
    ESQUIS("esquís", "esquís", false),
    TABLA_SNOWBOARD("tabla de snowboard", "tablas de snowboard", true),
    PELOTA("pelota", "pelotas", true),
    COMETA("cometa", "cometas", true),
    BATE_BEISBOL("bate de béisbol", "bates de béisbol", false),
    GUANTE_BEISBOL("guante de béisbol", "guantes de béisbol", false),
    MONOPATIN("monopatín", "monopatines", false),
    TABLA_SURF("tabla de surf", "tablas de surf", true),
    RAQUETA_TENIS("raqueta de tenis", "raquetas de tenis", true),
    BOTELLA("botella", "botellas", true),
    COPA_VINO("copa de vino", "copas de vino", true),
    TAZA("taza", "tazas", true),
    TENEDOR("tenedor", "tenedores", false),
    CUCHILLO("cuchillo", "cuchillos", false),
    CUCHARA("cuchara", "cucharas", true),
    CUENCO("cuenco", "cuencos", false),
    PLATANO("plátano", "plátanos", false),
    MANZANA("manzana", "manzanas", true),
    SANDWICH("sándwich", "sándwiches", false),
    NARANJA("naranja", "naranjas", true),
    BROCOLI("brócoli", "brócolis", false),
    ZANAHORIA("zanahoria", "zanahorias", true),
    PERRITO_CALIENTE("perrito caliente", "perritos calientes", false),
    PIZZA("pizza", "pizzas", true),
    DONUT("donut", "donuts", false),
    TARTA("tarta", "tartas", true),
    SILLA("silla", "sillas", true),
    SOFA("sofá", "sofás", false),
    PLANTA("planta en maceta", "plantas en maceta", true),
    CAMA("cama", "camas", true),
    MESA("mesa", "mesas", true),
    INODORO("inodoro", "inodoros", false),
    TELEVISOR("televisor", "televisores", false),
    PORTATIL("portátil", "portátiles", false),
    RATON("ratón", "ratones", false),
    MANDO("mando a distancia", "mandos a distancia", false),
    TECLADO("teclado", "teclados", false),
    TELEFONO("teléfono móvil", "teléfonos móviles", false),
    MICROONDAS("microondas", "microondas", false),
    HORNO("horno", "hornos", false),
    TOSTADORA("tostadora", "tostadoras", true),
    FREGADERO("fregadero", "fregaderos", false),
    NEVERA("nevera", "neveras", true),
    LIBRO("libro", "libros", false),
    RELOJ("reloj", "relojes", false),
    JARRON("jarrón", "jarrones", false),
    TIJERAS("tijeras", "tijeras", true),
    OSO_PELUCHE("oso de peluche", "osos de peluche", false),
    SECADOR("secador de pelo", "secadores de pelo", false),
    CEPILLO("cepillo de dientes", "cepillos de dientes", false);

    private final String singular;
    private final String plural;
    private final boolean femenino;

    ClaseCoco(String singular, String plural, boolean femenino) {
        this.singular = singular;
        this.plural = plural;
        this.femenino = femenino;
    }

    /** Índice de clase del modelo (0 = persona ... 79 = cepillo de dientes). */
    public static ClaseCoco desde(int indice) {
        ClaseCoco[] valores = values();
        if (indice < 0 || indice >= valores.length) {
            throw new IllegalArgumentException("Índice de clase COCO fuera de rango: " + indice);
        }
        return valores[indice];
    }

    public String etiqueta() {
        return singular;
    }

    public String plural() {
        return plural;
    }

    public boolean esFemenino() {
        return femenino;
    }
}

# Pruebas-Unitarias-Reactive-Backend

## Primer Test con StepVerifier

Crear un proyecto de Spring Boot que incluya las siguientes dependencias, puedes hacer uso de Spring Initializr

```
<dependencies>
	<dependency>
			<groupId>org.springframework.boot</groupId>
			<artifactId>spring-boot-starter-webflux</artifactId>
	</dependency>

	<dependency>
			<groupId>org.springframework.boot</groupId>
			<artifactId>spring-boot-starter-test</artifactId>
			<scope>test</scope>
	</dependency>
	<dependency>
			<groupId>io.projectreactor</groupId>
			<artifactId>reactor-test</artifactId>
			<scope>test</scope>
	</dependency>
</dependencies>
```

Una ves tengamos nuestro proyecto crearemos una clase de servicio que devuelva algunos Flux y Monos

```java
@Service
public class Servicio {
    public Mono<String> buscarUno() {
        return Mono.just("Pedro");
    }
    public Flux<String> buscarTodos() {
        return Flux.just("Pedro", "Maria", "Jesus", "Carmen");
    }
    public Flux<String> buscarTodosLento() {
        return Flux.just("Pedro", "Maria", "Jesus", "Carmen").delaySequence(Duration.ofSeconds(20));
    }
}
```

Hecho esto creamos una clase de pruebas para construir nuestros test.

```java
@SpringBootTest
class ServicioTest {
    @Autowired
    Servicio servicio;
    @Test
    void testMono() {
        Mono<String> uno = servicio.buscarUno();
        StepVerifier.create(uno).expectNext("Pedro").verifyComplete();
    }
    @Test
    void testVarios() {
        Flux<String> uno = servicio.buscarTodos();
        StepVerifier.create(uno).expectNext("Pedro").expectNext("Maria").expectNext("Jesus").expectNext("Carmen").verifyComplete();
    }
}
```

En estos dos primeros test la clase StepVerifier verifica casi que de forma directa el contenido esperado del Mono (un elemento) y Flux (listado de elementos) mediante expectNext() y verifyComplete() comprobara el contenido del flujo de datos que nos llega. Sin embargo tenemos un método buscarTodosLento() donde de manera asíncrona los elementos se irán presentando un periodo de espera definido, ¿como podemos probar que este método funciona como se espera?

```java
@Test
    void testVariosLento() {
        Flux<String> uno = servicio.buscarTodosLento();
        StepVerifier.create(uno)
                .expectNext("Pedro")
                .thenAwait(Duration.ofSeconds(1))
                .expectNext("Maria")
                .thenAwait(Duration.ofSeconds(1))
                .expectNext("Jesus")
                .thenAwait(Duration.ofSeconds(1))
                .expectNext("Carmen")
                .thenAwait(Duration.ofSeconds(1)).verifyComplete();
    }
 ```
 
>Como puedes ver en este nuevo test podemos agregar periodos de espera con thenAwait() que le dan la capacidad de esperar a que el nuevo dato llegue. Si ejecutas los test anteriores evidenciaras que el test tarda 20 segundos en ejecutarse el cual es el tiempo que hemos configurado de espera para el método lento.

Ahora que hemos visto lo básico veamos algunos usos avanzados de StepVerifier.

Para ello creemos un método publicador que emita un Flux. Este Flux lo crearemos genere solo nombres de cuatro letras mapeados en mayúsculas:

```java
public Flux<String> buscarTodosFiltro() {
        Flux<String> source = Flux.just("John", "Monica", "Mark", "Cloe", "Frank", "Casper", "Olivia", "Emily", "Cate")
                .filter(name -> name.length() == 4)
                .map(String::toUpperCase);
        return source;
    }
```

Para probar este nuevo publicador creamos un stepverifier para probar que ocurre cuando alguien se suscribe.

```java
@Test
    void testTodosFiltro() {
        Flux<String> source = servicio.buscarTodosFiltro();
        StepVerifier
                .create(source)
                .expectNext("JOHN")
                .expectNextMatches(name -> name.startsWith("MA"))
                .expectNext("CLOE", "CATE")
                .expectComplete()
                .verify();
    }
```
> Teniendo en cuenta que nuestro publicador solo emitirá nombres en mayúsculas con hasta 4 caracteres podemos crear un stepverifier que compruebe: El primer elemento sera "JOHN", el siguiente sera un nombre que comienza por "MA" y los dos siguientes corresponden a "CLOE" y "CATE", finalmente indicamos que nuestro flujo debería terminar y verificamos.

## Exceptions

Concatenemos un Mono a un Flux, de manera que el Mono termine inmediatamente con un error cuando se suscriba a nuestro publicador de nombre.

```java
Flux<String> error = source.concatWith(
                Mono.error(new IllegalArgumentException("Mensaje de Error"))
        );
```

Ahora nuestro StepVerifier después de contar cuatro elementos esperara que nuestro flujo termine con la excepción.

```java
@Test
    void testTodosFiltro() {
        Flux<String> source = servicio.buscarTodosFiltro();
        StepVerifier
                .create(source)
                .expectNextCount(4)
                .expectErrorMatches(throwable -> throwable instanceof IllegalArgumentException &&
                        throwable.getMessage().equals("Mensaje de Error")
                ).verify();
    }
```

Solo podemos usar un método para verificar las excepciones. La señal OnError notifica al suscriptor que el editor está cerrado con un estado de error. Por lo tanto, no podemos agregar más expectativas después.

Si no es necesario verificar el tipo y el mensaje de la excepción a la vez, podemos usar uno de los métodos dedicados:

  - expectError() - esperar cualquier tipo de error
  - expectError(Class<? extends Throwable> class) - espera un error de un tipo específico
  - expectErrorMessage(String errorMessage) - se espera un error con un mensaje específico
  - expectErrorMatches(Predicate<Throwable> predicate) - se espera un error que coincida con un predicado dado
  - expectErrorSatisfies(Consumer<Throwable> assertionConsumer) - consume un Throwable para hacer una aserción personalizada

## Publicadores Basados en Tiempo

Por ejemplo, suponga que en nuestra aplicación de la vida real, tenemos un retraso de un día entre eventos. Ahora, obviamente, no queremos que nuestras pruebas se ejecuten durante todo un día para verificar el comportamiento esperado con tal retraso. El constructor StepVerifier.withVirtualTime está diseñado para evitar pruebas de larga duración.

```java
StepVerifier
  .withVirtualTime(() -> Flux.interval(Duration.ofSeconds(1)).take(2))
  .expectSubscription()
  .expectNoEvent(Duration.ofSeconds(1))
  .expectNext(0L)
  .thenAwait(Duration.ofSeconds(1))
  .expectNext(1L)
  .verifyComplete();
```

Hay dos métodos principales de expectativa que se ocupan del tiempo:

  - thenAwait(Duration duration) - detiene la evaluación de los pasos; pueden ocurrir nuevos eventos durante este tiempo
  - expectNoEvent(Duration duration) - falla cuando aparece algún evento durante la duración; la secuencia pasará con una duración determinada

## Afirmaciones posteriores a la ejecución

Los elementos que hemos visto previamente cubren la mayoría de escenarios de prueba para flujos reactivos, sin embargo a veces necesitamos verificar un estado adicional después de que todo nuestro escenario se desarrolló con éxito. 

Para simular esta situación creemos un publicador personalizado que emita algunos elementos luego al completar, pausará y emitirá un elemento más.

```java
Flux<Integer> source = Flux.<Integer>create(emitter -> {
    emitter.next(1);
    emitter.next(2);
    emitter.next(3);
    emitter.complete();
    try {
        Thread.sleep(1000);
    } catch (InterruptedException e) {
        e.printStackTrace();
    }
    emitter.next(4);
}).filter(number -> number % 2 == 0);
```

Con esto esperamos que se emita un 2 y se suelte un 4 pues hemos marcado como completado antes de esta ultima emisión. Para verificar este comportamiento creamos nuestro test de la siguiente manera:

```java
StepVerifier.create(source)
      .expectNext(2)
      .expectComplete()
      .verifyThenAssertThat()
      .hasDropped(4)
      .tookLessThan(Duration.ofMillis(1050));
}
```

# Testing Reactive Streams

>TestPublisher

En ocasiones y bajo ciertos casos de prueba es posible que necesitemos algunos datos especiales para activar ciertos comportamientos concretos que queramos probar de un publicador especifico. TestPublisher<T>, nos permitirá activar señales de prueba como si de un publicador real se tratara, los métodos mas comunes son:

  - next(T value) o next(T value, T rest) - envía una o más señales a los suscriptores
  - emit(T Value) - igual que next (T) pero invoca complete() al terminar
  - complete() - termina la fuente con la señal completar
  - error(Throwable tr) - termina una fuente con un error
  - flux() - método para envolver un TestPublisher en Flux
  - mono() - lo mismo que flux() pero envolviéndolo en Mono

## Usando TestPublisher

Podemos crear un TestPublisher simple que emita un par de datos y termine con una excepción.

```java
TestPublisher
  .<String>create()
  .next("Primero", "Segundo", "Tercero")
  .error(new RuntimeException("Message"));
```

El ejemplo anterior es muy trivial, y no podemos evidenciar los beneficios de usar un TestPublisher sobre un publicador particular. Como mencionamos anteriormente, a veces es posible que deseemos activar una señal concreta que coincida estrechamente con una situación particular en nuestro caso de prueba. Evidenciemos esto en el siguiente ejemplo:

Crearemos una clase que use Flux<String> como parámetro constructor el cual mediante el método getUpperCase() convertira este flujo de Strings a mayúsculas. 

```java
class UppercaseConverter {
    private final Flux<String> source;
    UppercaseConverter(Flux<String> source) {
        this.source = source;
    }
    Flux<String> getUpperCase() {
        return source
          .map(String::toUpperCase);
    }   
}
```

Supongamos que esta clase es muy compleja, y necesitamos proporcionar datos muy particulares para un caso de prueba. Podemos usar TestPublisher para generar estos datos sin la complejidad del publicador original. 

```java
final TestPublisher<String> testPublisher = TestPublisher.create();
    @Test
    void testUpperCase() {
        UppercaseConverter uppercaseConverter = new UppercaseConverter(testPublisher.flux());
        StepVerifier.create(uppercaseConverter.getUpperCase())
                .then(() -> testPublisher.emit("datos", "GeNeRaDoS", "Sofka"))
                .expectNext("DATOS", "GENERADOS", "SOFKA")
                .verifyComplete();
    }
```

En este ejemplo, creamos un TestPublisher de Flux de prueba en el parámetro del constructor UppercaseConverter. Luego, nuestro TestPublisher emite tres elementos y completa de esta manera la prueba de la clase sin hacer uso del publicador original de los datos.

En el caso anterior creamos un TestPublisher que emite datos para poder probar un componente especifico, también podemos simular casos de comportamientos inesperados (misbehaving) de un posible publicador. En el siguiente ejemplo podemos simular un publicador que emite una serie de números, uno de los cuales va nulo, en circunstancias normales este comportamiento arrojaria un NullPointException que es precisamente lo que queremos probar. 

```java
TestPublisher
  .createNoncompliant(TestPublisher.Violation.ALLOW_NULL)
  .emit("1", "2", null, "3");
```

De esta manera nuestro TestPublisher puede enviar datos concretos para probar errores entre la comunicación publicador-suscriptor. Además de ALLOW_NULL podemos configurar algunos otros comportamientos típicos que ocasionarían errores. 
  - REQUEST_OVERFLOW – permite llamar a next() sin lanzar una IllegalStateException cuando hay un número insuficiente de solicitudes.
  - CLEANUP_ON_TERMINATE – permite enviar varias señales de terminación consecutivamente.
  - DEFER_CANCELLATION – nos permite ignorar las señales de cancelación y continuar con la emisión de elementos

package co.com.sofka.reactive;

import reactor.core.publisher.Flux;

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

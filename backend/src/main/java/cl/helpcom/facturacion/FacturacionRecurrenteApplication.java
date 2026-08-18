package cl.helpcom.facturacion;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class FacturacionRecurrenteApplication {

    public static void main(String[] args) {
        SpringApplication.run(FacturacionRecurrenteApplication.class, args);
    }
}

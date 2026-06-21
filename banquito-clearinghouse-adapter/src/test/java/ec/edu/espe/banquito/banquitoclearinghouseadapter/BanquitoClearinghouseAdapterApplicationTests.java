package ec.edu.espe.banquito.banquitoclearinghouseadapter;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
        "spring.mongodb.uri=mongodb://localhost:27017/clearingdb"
})
class BanquitoClearinghouseAdapterApplicationTests {

    @Test
    void contextLoads() {
        // Verifica que el contexto de Spring carga correctamente con toda la configuracion.
    }

}

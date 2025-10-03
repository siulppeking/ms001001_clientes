/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/springframework/RestController.java to edit this template
 */
package pe.mil.fap.controller;

import com.fasterxml.jackson.databind.JsonNode;
import pe.mil.fap.entities.Cliente;
import pe.mil.fap.entities.ClienteProducto;
import pe.mil.fap.repository.CustomerRepository;
import io.netty.channel.ChannelOption;
import io.netty.channel.epoll.EpollChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import java.time.Duration;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestMapping;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import java.util.Collections;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

/**
 *
 * @author dukz
 */
@RestController
@RequestMapping("/api/clientes")
public class ClienteRestController {

    @Autowired
    private Environment env;

    @Autowired
    CustomerRepository customerRepository;

    @Autowired
    private WebClient.Builder webClientBuilder;

    //webClient requires HttpClient library to work propertly       
    HttpClient client = HttpClient.create()
            //Connection Timeout: is a period within which a connection between a client and a server must be established
            .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 5000)
            .option(ChannelOption.SO_KEEPALIVE, true)
            .option(EpollChannelOption.TCP_KEEPIDLE, 300)
            .option(EpollChannelOption.TCP_KEEPINTVL, 60)
            //Response Timeout: The maximun time we wait to receive a response after sending a request
            .responseTimeout(Duration.ofSeconds(1))
            // Read and Write Timeout: A read timeout occurs when no data was read within a certain 
            //period of time, while the write timeout when a write operation cannot finish at a specific time
            .doOnConnected(connection -> {
                connection.addHandlerLast(new ReadTimeoutHandler(5000, TimeUnit.MILLISECONDS));
                connection.addHandlerLast(new WriteTimeoutHandler(5000, TimeUnit.MILLISECONDS));
            });

    @GetMapping("/check")
    public String check() {
        return "Hello your property is: " + env.getProperty("custom.activeprofileName");
    }

    @GetMapping()
    public List<Cliente> list() {
        return customerRepository.findAll();
    }

    @GetMapping("/{id}")
    public Cliente get(@PathVariable(name = "id") long id) {
        return customerRepository.findById(id).get();
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> put(@PathVariable(name = "id") long id, @RequestBody Cliente input) {
        Cliente find = customerRepository.findById(id).get();
        if (find != null) {
            find.setCode(input.getCode());
            find.setName(input.getName());
            find.setIban(input.getIban());
            find.setPhone(input.getPhone());
            find.setSurname(input.getSurname());
        }
        Cliente save = customerRepository.save(find);
        return ResponseEntity.ok(save);
    }

    @PostMapping
    public ResponseEntity<?> post(@RequestBody Cliente input) {
        input.getProducts().forEach(x -> x.setCliente(input));
        Cliente save = customerRepository.save(input);
        return ResponseEntity.ok(save);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable(name = "id") long id) {
        Optional<Cliente> findById = customerRepository.findById(id);
        if (findById.get() != null) {
            customerRepository.delete(findById.get());
        }
        return ResponseEntity.ok().build();
    }

    @GetMapping("/full")
    public Cliente getByCode(@RequestParam(name = "code") String code) {
        Cliente customer = customerRepository.findByCode(code);
        List<ClienteProducto> products = customer.getProducts();
        products.forEach(x -> {
            String productName = getProductName(x.getId());
            x.setProductName(productName);
        });
        //find all transactions that belong this account number
        List<?> transactions = getTransactions(customer.getIban());
        customer.setTransactions(transactions);
        return customer;

    }

    private String getProductName(long id) {
        WebClient build = webClientBuilder.clientConnector(new ReactorClientHttpConnector(client))
                .baseUrl("http://ms001001-productos/api/productos")
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .defaultUriVariables(Collections.singletonMap("url", "http://ms001001-productos/api/productos"))
                .build();
        JsonNode block = build.method(HttpMethod.GET).uri("/" + id)
                .retrieve().bodyToMono(JsonNode.class).block();
        String name = block.get("name").asText();
        return name;
    }

    /**
     * Call Transaction Microservice and Find all transaction that belong to the
     * account give
     *
     * @param iban account number of the customer
     * @return All transaction that belong this account
     */
    private List<?> getTransactions(String iban) {
        WebClient build = webClientBuilder.clientConnector(new ReactorClientHttpConnector(client))
                .baseUrl("http://ms001001-transacciones/api/transacciones")
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();

        Optional<List<?>> transactionsOptional = Optional.ofNullable(build.method(HttpMethod.GET)
                .uri(uriBuilder -> uriBuilder
                .path("/cliente/transacciones")
                .queryParam("ibanAccount", iban)
                .build())
                .retrieve()
                .bodyToFlux(Object.class)
                .collectList()
                .block());

        return transactionsOptional.orElse(Collections.emptyList());
    }
}

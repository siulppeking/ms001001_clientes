/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Interface.java to edit this template
 */
package pe.mil.fap.repository;

import pe.mil.fap.entities.Cliente;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/**
 *
 * @author dukz
 */
public interface CustomerRepository extends JpaRepository<Cliente, Long> {

    @Query("SELECT c FROM Cliente c WHERE c.code = ?1")
    public Cliente findByCode(String code);

    @Query("SELECT c FROM Cliente c WHERE c.iban = ?1")
    public Cliente findByAccount(String iban);

}

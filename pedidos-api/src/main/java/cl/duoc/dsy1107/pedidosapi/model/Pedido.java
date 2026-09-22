package cl.duoc.dsy1107.pedidosapi.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "pedidos")
public class Pedido {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Claim "oid" del token: identificador estable del usuario en Entra ID.
    @Column(nullable = false, length = 64)
    private String clienteId;

    // Claim "preferred_username", solo para mostrar.
    @Column(nullable = false)
    private String clienteNombre;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EstadoPedido estado;

    @Column(nullable = false)
    private BigDecimal total;

    @Column(nullable = false)
    private Instant creadoEn;

    @Column(nullable = false)
    private Instant actualizadoEn;

    @OneToMany(mappedBy = "pedido", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ItemPedido> items = new ArrayList<>();

    protected Pedido() {
    }

    public Pedido(String clienteId, String clienteNombre) {
        this.clienteId = clienteId;
        this.clienteNombre = clienteNombre;
        this.estado = EstadoPedido.CREADO;
        this.total = BigDecimal.ZERO;
    }

    @PrePersist
    void alCrear() {
        creadoEn = Instant.now();
        actualizadoEn = creadoEn;
    }

    @PreUpdate
    void alActualizar() {
        actualizadoEn = Instant.now();
    }

    public void agregarItem(ItemPedido item) {
        item.setPedido(this);
        items.add(item);
        total = total.add(item.getSubtotal());
    }

    public Long getId() {
        return id;
    }

    public String getClienteId() {
        return clienteId;
    }

    public String getClienteNombre() {
        return clienteNombre;
    }

    public EstadoPedido getEstado() {
        return estado;
    }

    public void setEstado(EstadoPedido estado) {
        this.estado = estado;
    }

    public BigDecimal getTotal() {
        return total;
    }

    public Instant getCreadoEn() {
        return creadoEn;
    }

    public Instant getActualizadoEn() {
        return actualizadoEn;
    }

    public List<ItemPedido> getItems() {
        return items;
    }
}

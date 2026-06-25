package room_type;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(name = "room_type")
public class Room_type {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name" ,nullable = false)
    private String name;

    @Column(name = "description" ,nullable = false , columnDefinition = "TEXT")
    private String description;

    @Column(name = "base_price" ,nullable = false)
    private Double base_price;

    @Column(name = "max_occupancy" ,nullable = false)
    private Integer max_occupansy;

    @Column(name = "amentities" ,nullable = false , columnDefinition = "TEXT")
    private String amentities;

    @Column(name = "img_url" ,nullable = false)
    private Integer img_url;

}

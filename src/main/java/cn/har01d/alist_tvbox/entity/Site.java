package cn.har01d.alist_tvbox.entity;

import lombok.*;
import org.hibernate.Hibernate;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import java.util.Objects;

@Getter
@Setter
@ToString
@RequiredArgsConstructor
@Entity
public class Site {
    @Id
    @GeneratedValue
    private Integer id;

    private String name;
    private String url;
    private String searchApi;
    private String indexFile;
    private boolean searchable;
    @Column(name = "`order`")
    private int order;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || Hibernate.getClass(this) != Hibernate.getClass(o)) return false;
        Site site = (Site) o;
        return id != null && Objects.equals(id, site.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}

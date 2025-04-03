package com.wiley.cms.cochrane.cmanager.specrender.data;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import javax.persistence.CascadeType;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.NamedQueries;
import javax.persistence.NamedQuery;
import javax.persistence.OneToMany;
import javax.persistence.OrderBy;
import javax.persistence.Table;

import com.wiley.cms.cochrane.cmanager.data.ClDbEntity;

/**
 * Type comments here.
 *
 * @author <a href='mailto:vKhalajiev@wiley.ru'>Vadim Khalajiev</a>
 * @version 1.0
 */
@Entity
@Table(name = "COCHRANE_SPEC_RENDERING")
@NamedQueries({
        @NamedQuery(
                name = "deleteSpecRndByDb",
                query = "delete from SpecRenderingEntity where db.id=:dbId"
        )
    })
public class SpecRenderingEntity implements java.io.Serializable {
    private Integer id;
    private ClDbEntity db;

    private Date date;

    private boolean isCompleted;
    private boolean isSuccessful;

    private List<SpecRenderingFileEntity> files;

    void fillIn(SpecRenderingVO vo) {
        setDate(vo.getDate());
        setCompleted(vo.isCompleted());
        setSuccessful(vo.isSuccessful());
    }

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    @ManyToOne
    @JoinColumn(name = "db_id")
    public ClDbEntity getDb() {
        return db;
    }

    public void setDb(ClDbEntity db) {
        this.db = db;
    }

    public Date getDate() {
        return date;
    }

    public void setDate(Date date) {
        this.date = date;
    }

    public boolean isSuccessful() {
        return isSuccessful;
    }

    public void setSuccessful(boolean successful) {
        isSuccessful = successful;
    }

    public boolean isCompleted() {
        return isCompleted;
    }

    public void setCompleted(boolean completed) {
        isCompleted = completed;
    }

    @OneToMany(cascade = CascadeType.ALL, fetch = FetchType.EAGER, mappedBy = "specRendering")
    @OrderBy("id ASC")
    public List<SpecRenderingFileEntity> getFiles() {
        return files;
    }

    public void addFile(SpecRenderingFileVO fileVO) {
        if (files == null) {
            files = new ArrayList<SpecRenderingFileEntity>();
        }

        SpecRenderingFileEntity fileEntity = new SpecRenderingFileEntity();
        fileEntity.setSpecRendering(this);
        fileEntity.setCompleted(fileVO.isCompleted());
        fileEntity.setSuccessful(fileVO.isSuccessful());
        fileEntity.setDate(fileVO.getDate());
        fileEntity.setFilePathPublish(fileVO.getFilePathPublish());
        fileEntity.setFilePathLocal(fileVO.getFilePathLocal());
        fileEntity.setItemAmount(fileVO.getItemAmount());

        files.add(fileEntity);
    }

    public void setFiles(List<SpecRenderingFileEntity> files) {
        this.files = files;
    }

}

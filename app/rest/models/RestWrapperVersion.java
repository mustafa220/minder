package rest.models;

import javax.xml.bind.annotation.*;
import java.util.Date;

/**
 * <p>Java class for WrapperVersion complex type.
 * <p>
 * <p>The following schema fragment specifies the expected content contained within this class.
 * <p>
 * <pre>
 * &lt;complexType name="wrapperVersion">
 *   &lt;complexContent>
 *     &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType">
 *       &lt;sequence>
 *         &lt;element name="id" type="{http://www.w3.org/2001/XMLSchema}string"/>
 *         &lt;element name="wrapperName" type="{http://www.w3.org/2001/XMLSchema}string"/>
 *         &lt;element name="version" type="{http://www.w3.org/2001/XMLSchema}string"/>
 *         &lt;element name="creationDate" type="{http://www.w3.org/2001/XMLSchema}date"/>
 *         &lt;element name="isAvailable" type="{http://www.w3.org/2001/XMLSchema}boolean"/>
 *       &lt;/sequence>
 *     &lt;/restriction>
 *   &lt;/complexContent>
 * &lt;/complexType>
 * </pre>
 *
 * @author: Melis Ozgur Cetinkaya Demir
 * @date: 17/11/15.
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "wrapperVersion", propOrder = {
        "wrapperVersionId",
        "wrapperName",
        "version",
        "creationDate",
        "isAvailable"
})
@XmlRootElement(name = "wrapperVersion")
public class RestWrapperVersion {

    @XmlElement(required = false)
    protected String wrapperVersionId;

    @XmlElement(required = false)
    protected String wrapperName;

    @XmlElement(required = false)
    protected String version;


    @XmlElement(required = false)
    protected Date creationDate;

    @XmlElement(required = false)
    protected boolean isAvailable;

    public String getWrapperVersionId() {
        return wrapperVersionId;
    }

    public void setWrapperVersionId(String wrapperVersionId) {
        this.wrapperVersionId = wrapperVersionId;
    }

    public String getWrapperName() {
        return wrapperName;
    }

    public void setWrapperName(String wrapperName) {
        this.wrapperName = wrapperName;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public Date getCreationDate() {
        return creationDate;
    }

    public void setCreationDate(Date creationDate) {
        this.creationDate = creationDate;
    }

    public boolean isAvailable() {
        return isAvailable;
    }

    public void setAvailable(boolean isAvailable) {
        this.isAvailable = isAvailable;
    }
}

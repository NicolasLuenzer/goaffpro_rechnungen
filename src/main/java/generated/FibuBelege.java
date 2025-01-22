//
// Diese Datei wurde mit der JavaTM Architecture for XML Binding(JAXB) Reference Implementation, v2.3.2 generiert 
// Siehe <a href="https://javaee.github.io/jaxb-v2/">https://javaee.github.io/jaxb-v2/</a> 
// Änderungen an dieser Datei gehen bei einer Neukompilierung des Quellschemas verloren. 
// Generiert: 2025.01.22 um 11:04:06 AM CET 
//


package generated;

import java.util.ArrayList;
import java.util.List;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java-Klasse für anonymous complex type.
 * 
 * <p>Das folgende Schemafragment gibt den erwarteten Content an, der in dieser Klasse enthalten ist.
 * 
 * <pre>
 * &lt;complexType&gt;
 *   &lt;complexContent&gt;
 *     &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType"&gt;
 *       &lt;sequence&gt;
 *         &lt;element name="firmaNr"&gt;
 *           &lt;simpleType&gt;
 *             &lt;restriction base="{http://www.w3.org/2001/XMLSchema}string"&gt;
 *               &lt;enumeration value="20"/&gt;
 *             &lt;/restriction&gt;
 *           &lt;/simpleType&gt;
 *         &lt;/element&gt;
 *         &lt;element name="FibuBeleg" maxOccurs="unbounded"&gt;
 *           &lt;complexType&gt;
 *             &lt;complexContent&gt;
 *               &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType"&gt;
 *                 &lt;sequence&gt;
 *                   &lt;element name="Belegkopf"&gt;
 *                     &lt;complexType&gt;
 *                       &lt;complexContent&gt;
 *                         &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType"&gt;
 *                           &lt;sequence&gt;
 *                             &lt;element name="belegart" type="{http://www.w3.org/2001/XMLSchema}string"/&gt;
 *                             &lt;element name="belegnummer"&gt;
 *                               &lt;simpleType&gt;
 *                                 &lt;restriction base="{http://www.w3.org/2001/XMLSchema}string"&gt;
 *                                   &lt;enumeration value="AUTO"/&gt;
 *                                 &lt;/restriction&gt;
 *                               &lt;/simpleType&gt;
 *                             &lt;/element&gt;
 *                             &lt;element name="belegdatum" type="{http://www.w3.org/2001/XMLSchema}string"/&gt;
 *                             &lt;element name="bruttoErfassung"&gt;
 *                               &lt;simpleType&gt;
 *                                 &lt;restriction base="{http://www.w3.org/2001/XMLSchema}string"&gt;
 *                                   &lt;enumeration value="j"/&gt;
 *                                 &lt;/restriction&gt;
 *                               &lt;/simpleType&gt;
 *                             &lt;/element&gt;
 *                             &lt;element name="buchungstext" type="{http://www.w3.org/2001/XMLSchema}string"/&gt;
 *                             &lt;element name="bucherKz"&gt;
 *                               &lt;simpleType&gt;
 *                                 &lt;restriction base="{http://www.w3.org/2001/XMLSchema}string"&gt;
 *                                   &lt;enumeration value="ACCOUNTONE"/&gt;
 *                                 &lt;/restriction&gt;
 *                               &lt;/simpleType&gt;
 *                             &lt;/element&gt;
 *                             &lt;element name="belegwaehrungskurs"&gt;
 *                               &lt;simpleType&gt;
 *                                 &lt;restriction base="{http://www.w3.org/2001/XMLSchema}string"&gt;
 *                                   &lt;enumeration value="1"/&gt;
 *                                 &lt;/restriction&gt;
 *                               &lt;/simpleType&gt;
 *                             &lt;/element&gt;
 *                             &lt;element name="belegwaehrung"&gt;
 *                               &lt;simpleType&gt;
 *                                 &lt;restriction base="{http://www.w3.org/2001/XMLSchema}string"&gt;
 *                                   &lt;enumeration value="EUR"/&gt;
 *                                 &lt;/restriction&gt;
 *                               &lt;/simpleType&gt;
 *                             &lt;/element&gt;
 *                             &lt;element name="referenznr" type="{http://www.w3.org/2001/XMLSchema}string"/&gt;
 *                           &lt;/sequence&gt;
 *                         &lt;/restriction&gt;
 *                       &lt;/complexContent&gt;
 *                     &lt;/complexType&gt;
 *                   &lt;/element&gt;
 *                   &lt;element name="FibuBelegpositionen"&gt;
 *                     &lt;complexType&gt;
 *                       &lt;complexContent&gt;
 *                         &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType"&gt;
 *                           &lt;sequence&gt;
 *                             &lt;element name="FibuBelegposition" maxOccurs="unbounded"&gt;
 *                               &lt;complexType&gt;
 *                                 &lt;complexContent&gt;
 *                                   &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType"&gt;
 *                                     &lt;sequence&gt;
 *                                       &lt;element name="buchungsschluessel" type="{http://www.w3.org/2001/XMLSchema}string"/&gt;
 *                                       &lt;element name="kontonummer" type="{http://www.w3.org/2001/XMLSchema}string"/&gt;
 *                                       &lt;element name="betrag" type="{http://www.w3.org/2001/XMLSchema}string"/&gt;
 *                                       &lt;element name="posLeistungsdatum" type="{http://www.w3.org/2001/XMLSchema}string" minOccurs="0"/&gt;
 *                                       &lt;element name="Opinfos" minOccurs="0"&gt;
 *                                         &lt;complexType&gt;
 *                                           &lt;complexContent&gt;
 *                                             &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType"&gt;
 *                                               &lt;sequence&gt;
 *                                                 &lt;element name="OpAngaben"&gt;
 *                                                   &lt;complexType&gt;
 *                                                     &lt;complexContent&gt;
 *                                                       &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType"&gt;
 *                                                         &lt;sequence&gt;
 *                                                           &lt;element name="opNr" type="{http://www.w3.org/2001/XMLSchema}string"/&gt;
 *                                                           &lt;element name="ustIdentNr" type="{http://www.w3.org/2001/XMLSchema}string"/&gt;
 *                                                           &lt;element name="opText" type="{http://www.w3.org/2001/XMLSchema}string"/&gt;
 *                                                           &lt;element name="verwendungszweck" type="{http://www.w3.org/2001/XMLSchema}string"/&gt;
 *                                                           &lt;element name="opBetrag" type="{http://www.w3.org/2001/XMLSchema}string"/&gt;
 *                                                         &lt;/sequence&gt;
 *                                                       &lt;/restriction&gt;
 *                                                     &lt;/complexContent&gt;
 *                                                   &lt;/complexType&gt;
 *                                                 &lt;/element&gt;
 *                                               &lt;/sequence&gt;
 *                                             &lt;/restriction&gt;
 *                                           &lt;/complexContent&gt;
 *                                         &lt;/complexType&gt;
 *                                       &lt;/element&gt;
 *                                       &lt;element name="steuerschluessel" type="{http://www.w3.org/2001/XMLSchema}string" minOccurs="0"/&gt;
 *                                     &lt;/sequence&gt;
 *                                   &lt;/restriction&gt;
 *                                 &lt;/complexContent&gt;
 *                               &lt;/complexType&gt;
 *                             &lt;/element&gt;
 *                           &lt;/sequence&gt;
 *                         &lt;/restriction&gt;
 *                       &lt;/complexContent&gt;
 *                     &lt;/complexType&gt;
 *                   &lt;/element&gt;
 *                 &lt;/sequence&gt;
 *               &lt;/restriction&gt;
 *             &lt;/complexContent&gt;
 *           &lt;/complexType&gt;
 *         &lt;/element&gt;
 *       &lt;/sequence&gt;
 *     &lt;/restriction&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "", propOrder = {
    "firmaNr",
    "fibuBeleg"
})
@XmlRootElement(name = "FibuBelege")
public class FibuBelege {

    @XmlElement(required = true)
    protected String firmaNr;
    @XmlElement(name = "FibuBeleg", required = true)
    protected List<FibuBelege.FibuBeleg> fibuBeleg;

    /**
     * Ruft den Wert der firmaNr-Eigenschaft ab.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getFirmaNr() {
        return firmaNr;
    }

    /**
     * Legt den Wert der firmaNr-Eigenschaft fest.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setFirmaNr(String value) {
        this.firmaNr = value;
    }

    /**
     * Gets the value of the fibuBeleg property.
     * 
     * <p>
     * This accessor method returns a reference to the live list,
     * not a snapshot. Therefore any modification you make to the
     * returned list will be present inside the JAXB object.
     * This is why there is not a <CODE>set</CODE> method for the fibuBeleg property.
     * 
     * <p>
     * For example, to add a new item, do as follows:
     * <pre>
     *    getFibuBeleg().add(newItem);
     * </pre>
     * 
     * 
     * <p>
     * Objects of the following type(s) are allowed in the list
     * {@link FibuBelege.FibuBeleg }
     * 
     * 
     */
    public List<FibuBelege.FibuBeleg> getFibuBeleg() {
        if (fibuBeleg == null) {
            fibuBeleg = new ArrayList<FibuBelege.FibuBeleg>();
        }
        return this.fibuBeleg;
    }


    /**
     * <p>Java-Klasse für anonymous complex type.
     * 
     * <p>Das folgende Schemafragment gibt den erwarteten Content an, der in dieser Klasse enthalten ist.
     * 
     * <pre>
     * &lt;complexType&gt;
     *   &lt;complexContent&gt;
     *     &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType"&gt;
     *       &lt;sequence&gt;
     *         &lt;element name="Belegkopf"&gt;
     *           &lt;complexType&gt;
     *             &lt;complexContent&gt;
     *               &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType"&gt;
     *                 &lt;sequence&gt;
     *                   &lt;element name="belegart" type="{http://www.w3.org/2001/XMLSchema}string"/&gt;
     *                   &lt;element name="belegnummer"&gt;
     *                     &lt;simpleType&gt;
     *                       &lt;restriction base="{http://www.w3.org/2001/XMLSchema}string"&gt;
     *                         &lt;enumeration value="AUTO"/&gt;
     *                       &lt;/restriction&gt;
     *                     &lt;/simpleType&gt;
     *                   &lt;/element&gt;
     *                   &lt;element name="belegdatum" type="{http://www.w3.org/2001/XMLSchema}string"/&gt;
     *                   &lt;element name="bruttoErfassung"&gt;
     *                     &lt;simpleType&gt;
     *                       &lt;restriction base="{http://www.w3.org/2001/XMLSchema}string"&gt;
     *                         &lt;enumeration value="j"/&gt;
     *                       &lt;/restriction&gt;
     *                     &lt;/simpleType&gt;
     *                   &lt;/element&gt;
     *                   &lt;element name="buchungstext" type="{http://www.w3.org/2001/XMLSchema}string"/&gt;
     *                   &lt;element name="bucherKz"&gt;
     *                     &lt;simpleType&gt;
     *                       &lt;restriction base="{http://www.w3.org/2001/XMLSchema}string"&gt;
     *                         &lt;enumeration value="ACCOUNTONE"/&gt;
     *                       &lt;/restriction&gt;
     *                     &lt;/simpleType&gt;
     *                   &lt;/element&gt;
     *                   &lt;element name="belegwaehrungskurs"&gt;
     *                     &lt;simpleType&gt;
     *                       &lt;restriction base="{http://www.w3.org/2001/XMLSchema}string"&gt;
     *                         &lt;enumeration value="1"/&gt;
     *                       &lt;/restriction&gt;
     *                     &lt;/simpleType&gt;
     *                   &lt;/element&gt;
     *                   &lt;element name="belegwaehrung"&gt;
     *                     &lt;simpleType&gt;
     *                       &lt;restriction base="{http://www.w3.org/2001/XMLSchema}string"&gt;
     *                         &lt;enumeration value="EUR"/&gt;
     *                       &lt;/restriction&gt;
     *                     &lt;/simpleType&gt;
     *                   &lt;/element&gt;
     *                   &lt;element name="referenznr" type="{http://www.w3.org/2001/XMLSchema}string"/&gt;
     *                 &lt;/sequence&gt;
     *               &lt;/restriction&gt;
     *             &lt;/complexContent&gt;
     *           &lt;/complexType&gt;
     *         &lt;/element&gt;
     *         &lt;element name="FibuBelegpositionen"&gt;
     *           &lt;complexType&gt;
     *             &lt;complexContent&gt;
     *               &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType"&gt;
     *                 &lt;sequence&gt;
     *                   &lt;element name="FibuBelegposition" maxOccurs="unbounded"&gt;
     *                     &lt;complexType&gt;
     *                       &lt;complexContent&gt;
     *                         &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType"&gt;
     *                           &lt;sequence&gt;
     *                             &lt;element name="buchungsschluessel" type="{http://www.w3.org/2001/XMLSchema}string"/&gt;
     *                             &lt;element name="kontonummer" type="{http://www.w3.org/2001/XMLSchema}string"/&gt;
     *                             &lt;element name="betrag" type="{http://www.w3.org/2001/XMLSchema}string"/&gt;
     *                             &lt;element name="posLeistungsdatum" type="{http://www.w3.org/2001/XMLSchema}string" minOccurs="0"/&gt;
     *                             &lt;element name="Opinfos" minOccurs="0"&gt;
     *                               &lt;complexType&gt;
     *                                 &lt;complexContent&gt;
     *                                   &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType"&gt;
     *                                     &lt;sequence&gt;
     *                                       &lt;element name="OpAngaben"&gt;
     *                                         &lt;complexType&gt;
     *                                           &lt;complexContent&gt;
     *                                             &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType"&gt;
     *                                               &lt;sequence&gt;
     *                                                 &lt;element name="opNr" type="{http://www.w3.org/2001/XMLSchema}string"/&gt;
     *                                                 &lt;element name="ustIdentNr" type="{http://www.w3.org/2001/XMLSchema}string"/&gt;
     *                                                 &lt;element name="opText" type="{http://www.w3.org/2001/XMLSchema}string"/&gt;
     *                                                 &lt;element name="verwendungszweck" type="{http://www.w3.org/2001/XMLSchema}string"/&gt;
     *                                                 &lt;element name="opBetrag" type="{http://www.w3.org/2001/XMLSchema}string"/&gt;
     *                                               &lt;/sequence&gt;
     *                                             &lt;/restriction&gt;
     *                                           &lt;/complexContent&gt;
     *                                         &lt;/complexType&gt;
     *                                       &lt;/element&gt;
     *                                     &lt;/sequence&gt;
     *                                   &lt;/restriction&gt;
     *                                 &lt;/complexContent&gt;
     *                               &lt;/complexType&gt;
     *                             &lt;/element&gt;
     *                             &lt;element name="steuerschluessel" type="{http://www.w3.org/2001/XMLSchema}string" minOccurs="0"/&gt;
     *                           &lt;/sequence&gt;
     *                         &lt;/restriction&gt;
     *                       &lt;/complexContent&gt;
     *                     &lt;/complexType&gt;
     *                   &lt;/element&gt;
     *                 &lt;/sequence&gt;
     *               &lt;/restriction&gt;
     *             &lt;/complexContent&gt;
     *           &lt;/complexType&gt;
     *         &lt;/element&gt;
     *       &lt;/sequence&gt;
     *     &lt;/restriction&gt;
     *   &lt;/complexContent&gt;
     * &lt;/complexType&gt;
     * </pre>
     * 
     * 
     */
    @XmlAccessorType(XmlAccessType.FIELD)
    @XmlType(name = "", propOrder = {
        "belegkopf",
        "fibuBelegpositionen"
    })
    public static class FibuBeleg {

        @XmlElement(name = "Belegkopf", required = true)
        protected FibuBelege.FibuBeleg.Belegkopf belegkopf;
        @XmlElement(name = "FibuBelegpositionen", required = true)
        protected FibuBelege.FibuBeleg.FibuBelegpositionen fibuBelegpositionen;

        /**
         * Ruft den Wert der belegkopf-Eigenschaft ab.
         * 
         * @return
         *     possible object is
         *     {@link FibuBelege.FibuBeleg.Belegkopf }
         *     
         */
        public FibuBelege.FibuBeleg.Belegkopf getBelegkopf() {
            return belegkopf;
        }

        /**
         * Legt den Wert der belegkopf-Eigenschaft fest.
         * 
         * @param value
         *     allowed object is
         *     {@link FibuBelege.FibuBeleg.Belegkopf }
         *     
         */
        public void setBelegkopf(FibuBelege.FibuBeleg.Belegkopf value) {
            this.belegkopf = value;
        }

        /**
         * Ruft den Wert der fibuBelegpositionen-Eigenschaft ab.
         * 
         * @return
         *     possible object is
         *     {@link FibuBelege.FibuBeleg.FibuBelegpositionen }
         *     
         */
        public FibuBelege.FibuBeleg.FibuBelegpositionen getFibuBelegpositionen() {
            return fibuBelegpositionen;
        }

        /**
         * Legt den Wert der fibuBelegpositionen-Eigenschaft fest.
         * 
         * @param value
         *     allowed object is
         *     {@link FibuBelege.FibuBeleg.FibuBelegpositionen }
         *     
         */
        public void setFibuBelegpositionen(FibuBelege.FibuBeleg.FibuBelegpositionen value) {
            this.fibuBelegpositionen = value;
        }


        /**
         * <p>Java-Klasse für anonymous complex type.
         * 
         * <p>Das folgende Schemafragment gibt den erwarteten Content an, der in dieser Klasse enthalten ist.
         * 
         * <pre>
         * &lt;complexType&gt;
         *   &lt;complexContent&gt;
         *     &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType"&gt;
         *       &lt;sequence&gt;
         *         &lt;element name="belegart" type="{http://www.w3.org/2001/XMLSchema}string"/&gt;
         *         &lt;element name="belegnummer"&gt;
         *           &lt;simpleType&gt;
         *             &lt;restriction base="{http://www.w3.org/2001/XMLSchema}string"&gt;
         *               &lt;enumeration value="AUTO"/&gt;
         *             &lt;/restriction&gt;
         *           &lt;/simpleType&gt;
         *         &lt;/element&gt;
         *         &lt;element name="belegdatum" type="{http://www.w3.org/2001/XMLSchema}string"/&gt;
         *         &lt;element name="bruttoErfassung"&gt;
         *           &lt;simpleType&gt;
         *             &lt;restriction base="{http://www.w3.org/2001/XMLSchema}string"&gt;
         *               &lt;enumeration value="j"/&gt;
         *             &lt;/restriction&gt;
         *           &lt;/simpleType&gt;
         *         &lt;/element&gt;
         *         &lt;element name="buchungstext" type="{http://www.w3.org/2001/XMLSchema}string"/&gt;
         *         &lt;element name="bucherKz"&gt;
         *           &lt;simpleType&gt;
         *             &lt;restriction base="{http://www.w3.org/2001/XMLSchema}string"&gt;
         *               &lt;enumeration value="ACCOUNTONE"/&gt;
         *             &lt;/restriction&gt;
         *           &lt;/simpleType&gt;
         *         &lt;/element&gt;
         *         &lt;element name="belegwaehrungskurs"&gt;
         *           &lt;simpleType&gt;
         *             &lt;restriction base="{http://www.w3.org/2001/XMLSchema}string"&gt;
         *               &lt;enumeration value="1"/&gt;
         *             &lt;/restriction&gt;
         *           &lt;/simpleType&gt;
         *         &lt;/element&gt;
         *         &lt;element name="belegwaehrung"&gt;
         *           &lt;simpleType&gt;
         *             &lt;restriction base="{http://www.w3.org/2001/XMLSchema}string"&gt;
         *               &lt;enumeration value="EUR"/&gt;
         *             &lt;/restriction&gt;
         *           &lt;/simpleType&gt;
         *         &lt;/element&gt;
         *         &lt;element name="referenznr" type="{http://www.w3.org/2001/XMLSchema}string"/&gt;
         *       &lt;/sequence&gt;
         *     &lt;/restriction&gt;
         *   &lt;/complexContent&gt;
         * &lt;/complexType&gt;
         * </pre>
         * 
         * 
         */
        @XmlAccessorType(XmlAccessType.FIELD)
        @XmlType(name = "", propOrder = {
            "belegart",
            "belegnummer",
            "belegdatum",
            "bruttoErfassung",
            "buchungstext",
            "bucherKz",
            "belegwaehrungskurs",
            "belegwaehrung",
            "referenznr"
        })
        public static class Belegkopf {

            @XmlElement(required = true)
            protected String belegart;
            @XmlElement(required = true)
            protected String belegnummer;
            @XmlElement(required = true)
            protected String belegdatum;
            @XmlElement(required = true)
            protected String bruttoErfassung;
            @XmlElement(required = true)
            protected String buchungstext;
            @XmlElement(required = true)
            protected String bucherKz;
            @XmlElement(required = true)
            protected String belegwaehrungskurs;
            @XmlElement(required = true)
            protected String belegwaehrung;
            @XmlElement(required = true)
            protected String referenznr;

            /**
             * Ruft den Wert der belegart-Eigenschaft ab.
             * 
             * @return
             *     possible object is
             *     {@link String }
             *     
             */
            public String getBelegart() {
                return belegart;
            }

            /**
             * Legt den Wert der belegart-Eigenschaft fest.
             * 
             * @param value
             *     allowed object is
             *     {@link String }
             *     
             */
            public void setBelegart(String value) {
                this.belegart = value;
            }

            /**
             * Ruft den Wert der belegnummer-Eigenschaft ab.
             * 
             * @return
             *     possible object is
             *     {@link String }
             *     
             */
            public String getBelegnummer() {
                return belegnummer;
            }

            /**
             * Legt den Wert der belegnummer-Eigenschaft fest.
             * 
             * @param value
             *     allowed object is
             *     {@link String }
             *     
             */
            public void setBelegnummer(String value) {
                this.belegnummer = value;
            }

            /**
             * Ruft den Wert der belegdatum-Eigenschaft ab.
             * 
             * @return
             *     possible object is
             *     {@link String }
             *     
             */
            public String getBelegdatum() {
                return belegdatum;
            }

            /**
             * Legt den Wert der belegdatum-Eigenschaft fest.
             * 
             * @param value
             *     allowed object is
             *     {@link String }
             *     
             */
            public void setBelegdatum(String value) {
                this.belegdatum = value;
            }

            /**
             * Ruft den Wert der bruttoErfassung-Eigenschaft ab.
             * 
             * @return
             *     possible object is
             *     {@link String }
             *     
             */
            public String getBruttoErfassung() {
                return bruttoErfassung;
            }

            /**
             * Legt den Wert der bruttoErfassung-Eigenschaft fest.
             * 
             * @param value
             *     allowed object is
             *     {@link String }
             *     
             */
            public void setBruttoErfassung(String value) {
                this.bruttoErfassung = value;
            }

            /**
             * Ruft den Wert der buchungstext-Eigenschaft ab.
             * 
             * @return
             *     possible object is
             *     {@link String }
             *     
             */
            public String getBuchungstext() {
                return buchungstext;
            }

            /**
             * Legt den Wert der buchungstext-Eigenschaft fest.
             * 
             * @param value
             *     allowed object is
             *     {@link String }
             *     
             */
            public void setBuchungstext(String value) {
                this.buchungstext = value;
            }

            /**
             * Ruft den Wert der bucherKz-Eigenschaft ab.
             * 
             * @return
             *     possible object is
             *     {@link String }
             *     
             */
            public String getBucherKz() {
                return bucherKz;
            }

            /**
             * Legt den Wert der bucherKz-Eigenschaft fest.
             * 
             * @param value
             *     allowed object is
             *     {@link String }
             *     
             */
            public void setBucherKz(String value) {
                this.bucherKz = value;
            }

            /**
             * Ruft den Wert der belegwaehrungskurs-Eigenschaft ab.
             * 
             * @return
             *     possible object is
             *     {@link String }
             *     
             */
            public String getBelegwaehrungskurs() {
                return belegwaehrungskurs;
            }

            /**
             * Legt den Wert der belegwaehrungskurs-Eigenschaft fest.
             * 
             * @param value
             *     allowed object is
             *     {@link String }
             *     
             */
            public void setBelegwaehrungskurs(String value) {
                this.belegwaehrungskurs = value;
            }

            /**
             * Ruft den Wert der belegwaehrung-Eigenschaft ab.
             * 
             * @return
             *     possible object is
             *     {@link String }
             *     
             */
            public String getBelegwaehrung() {
                return belegwaehrung;
            }

            /**
             * Legt den Wert der belegwaehrung-Eigenschaft fest.
             * 
             * @param value
             *     allowed object is
             *     {@link String }
             *     
             */
            public void setBelegwaehrung(String value) {
                this.belegwaehrung = value;
            }

            /**
             * Ruft den Wert der referenznr-Eigenschaft ab.
             * 
             * @return
             *     possible object is
             *     {@link String }
             *     
             */
            public String getReferenznr() {
                return referenznr;
            }

            /**
             * Legt den Wert der referenznr-Eigenschaft fest.
             * 
             * @param value
             *     allowed object is
             *     {@link String }
             *     
             */
            public void setReferenznr(String value) {
                this.referenznr = value;
            }

        }


        /**
         * <p>Java-Klasse für anonymous complex type.
         * 
         * <p>Das folgende Schemafragment gibt den erwarteten Content an, der in dieser Klasse enthalten ist.
         * 
         * <pre>
         * &lt;complexType&gt;
         *   &lt;complexContent&gt;
         *     &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType"&gt;
         *       &lt;sequence&gt;
         *         &lt;element name="FibuBelegposition" maxOccurs="unbounded"&gt;
         *           &lt;complexType&gt;
         *             &lt;complexContent&gt;
         *               &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType"&gt;
         *                 &lt;sequence&gt;
         *                   &lt;element name="buchungsschluessel" type="{http://www.w3.org/2001/XMLSchema}string"/&gt;
         *                   &lt;element name="kontonummer" type="{http://www.w3.org/2001/XMLSchema}string"/&gt;
         *                   &lt;element name="betrag" type="{http://www.w3.org/2001/XMLSchema}string"/&gt;
         *                   &lt;element name="posLeistungsdatum" type="{http://www.w3.org/2001/XMLSchema}string" minOccurs="0"/&gt;
         *                   &lt;element name="Opinfos" minOccurs="0"&gt;
         *                     &lt;complexType&gt;
         *                       &lt;complexContent&gt;
         *                         &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType"&gt;
         *                           &lt;sequence&gt;
         *                             &lt;element name="OpAngaben"&gt;
         *                               &lt;complexType&gt;
         *                                 &lt;complexContent&gt;
         *                                   &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType"&gt;
         *                                     &lt;sequence&gt;
         *                                       &lt;element name="opNr" type="{http://www.w3.org/2001/XMLSchema}string"/&gt;
         *                                       &lt;element name="ustIdentNr" type="{http://www.w3.org/2001/XMLSchema}string"/&gt;
         *                                       &lt;element name="opText" type="{http://www.w3.org/2001/XMLSchema}string"/&gt;
         *                                       &lt;element name="verwendungszweck" type="{http://www.w3.org/2001/XMLSchema}string"/&gt;
         *                                       &lt;element name="opBetrag" type="{http://www.w3.org/2001/XMLSchema}string"/&gt;
         *                                     &lt;/sequence&gt;
         *                                   &lt;/restriction&gt;
         *                                 &lt;/complexContent&gt;
         *                               &lt;/complexType&gt;
         *                             &lt;/element&gt;
         *                           &lt;/sequence&gt;
         *                         &lt;/restriction&gt;
         *                       &lt;/complexContent&gt;
         *                     &lt;/complexType&gt;
         *                   &lt;/element&gt;
         *                   &lt;element name="steuerschluessel" type="{http://www.w3.org/2001/XMLSchema}string" minOccurs="0"/&gt;
         *                 &lt;/sequence&gt;
         *               &lt;/restriction&gt;
         *             &lt;/complexContent&gt;
         *           &lt;/complexType&gt;
         *         &lt;/element&gt;
         *       &lt;/sequence&gt;
         *     &lt;/restriction&gt;
         *   &lt;/complexContent&gt;
         * &lt;/complexType&gt;
         * </pre>
         * 
         * 
         */
        @XmlAccessorType(XmlAccessType.FIELD)
        @XmlType(name = "", propOrder = {
            "fibuBelegposition"
        })
        public static class FibuBelegpositionen {

            @XmlElement(name = "FibuBelegposition", required = true)
            protected List<FibuBelege.FibuBeleg.FibuBelegpositionen.FibuBelegposition> fibuBelegposition;

            /**
             * Gets the value of the fibuBelegposition property.
             * 
             * <p>
             * This accessor method returns a reference to the live list,
             * not a snapshot. Therefore any modification you make to the
             * returned list will be present inside the JAXB object.
             * This is why there is not a <CODE>set</CODE> method for the fibuBelegposition property.
             * 
             * <p>
             * For example, to add a new item, do as follows:
             * <pre>
             *    getFibuBelegposition().add(newItem);
             * </pre>
             * 
             * 
             * <p>
             * Objects of the following type(s) are allowed in the list
             * {@link FibuBelege.FibuBeleg.FibuBelegpositionen.FibuBelegposition }
             * 
             * 
             */
            public List<FibuBelege.FibuBeleg.FibuBelegpositionen.FibuBelegposition> getFibuBelegposition() {
                if (fibuBelegposition == null) {
                    fibuBelegposition = new ArrayList<FibuBelege.FibuBeleg.FibuBelegpositionen.FibuBelegposition>();
                }
                return this.fibuBelegposition;
            }


            /**
             * <p>Java-Klasse für anonymous complex type.
             * 
             * <p>Das folgende Schemafragment gibt den erwarteten Content an, der in dieser Klasse enthalten ist.
             * 
             * <pre>
             * &lt;complexType&gt;
             *   &lt;complexContent&gt;
             *     &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType"&gt;
             *       &lt;sequence&gt;
             *         &lt;element name="buchungsschluessel" type="{http://www.w3.org/2001/XMLSchema}string"/&gt;
             *         &lt;element name="kontonummer" type="{http://www.w3.org/2001/XMLSchema}string"/&gt;
             *         &lt;element name="betrag" type="{http://www.w3.org/2001/XMLSchema}string"/&gt;
             *         &lt;element name="posLeistungsdatum" type="{http://www.w3.org/2001/XMLSchema}string" minOccurs="0"/&gt;
             *         &lt;element name="Opinfos" minOccurs="0"&gt;
             *           &lt;complexType&gt;
             *             &lt;complexContent&gt;
             *               &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType"&gt;
             *                 &lt;sequence&gt;
             *                   &lt;element name="OpAngaben"&gt;
             *                     &lt;complexType&gt;
             *                       &lt;complexContent&gt;
             *                         &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType"&gt;
             *                           &lt;sequence&gt;
             *                             &lt;element name="opNr" type="{http://www.w3.org/2001/XMLSchema}string"/&gt;
             *                             &lt;element name="ustIdentNr" type="{http://www.w3.org/2001/XMLSchema}string"/&gt;
             *                             &lt;element name="opText" type="{http://www.w3.org/2001/XMLSchema}string"/&gt;
             *                             &lt;element name="verwendungszweck" type="{http://www.w3.org/2001/XMLSchema}string"/&gt;
             *                             &lt;element name="opBetrag" type="{http://www.w3.org/2001/XMLSchema}string"/&gt;
             *                           &lt;/sequence&gt;
             *                         &lt;/restriction&gt;
             *                       &lt;/complexContent&gt;
             *                     &lt;/complexType&gt;
             *                   &lt;/element&gt;
             *                 &lt;/sequence&gt;
             *               &lt;/restriction&gt;
             *             &lt;/complexContent&gt;
             *           &lt;/complexType&gt;
             *         &lt;/element&gt;
             *         &lt;element name="steuerschluessel" type="{http://www.w3.org/2001/XMLSchema}string" minOccurs="0"/&gt;
             *       &lt;/sequence&gt;
             *     &lt;/restriction&gt;
             *   &lt;/complexContent&gt;
             * &lt;/complexType&gt;
             * </pre>
             * 
             * 
             */
            @XmlAccessorType(XmlAccessType.FIELD)
            @XmlType(name = "", propOrder = {
                "buchungsschluessel",
                "kontonummer",
                "betrag",
                "posLeistungsdatum",
                "opinfos",
                "steuerschluessel"
            })
            public static class FibuBelegposition {

                @XmlElement(required = true)
                protected String buchungsschluessel;
                @XmlElement(required = true)
                protected String kontonummer;
                @XmlElement(required = true)
                protected String betrag;
                protected String posLeistungsdatum;
                @XmlElement(name = "Opinfos")
                protected FibuBelege.FibuBeleg.FibuBelegpositionen.FibuBelegposition.Opinfos opinfos;
                protected String steuerschluessel;

                /**
                 * Ruft den Wert der buchungsschluessel-Eigenschaft ab.
                 * 
                 * @return
                 *     possible object is
                 *     {@link String }
                 *     
                 */
                public String getBuchungsschluessel() {
                    return buchungsschluessel;
                }

                /**
                 * Legt den Wert der buchungsschluessel-Eigenschaft fest.
                 * 
                 * @param value
                 *     allowed object is
                 *     {@link String }
                 *     
                 */
                public void setBuchungsschluessel(String value) {
                    this.buchungsschluessel = value;
                }

                /**
                 * Ruft den Wert der kontonummer-Eigenschaft ab.
                 * 
                 * @return
                 *     possible object is
                 *     {@link String }
                 *     
                 */
                public String getKontonummer() {
                    return kontonummer;
                }

                /**
                 * Legt den Wert der kontonummer-Eigenschaft fest.
                 * 
                 * @param value
                 *     allowed object is
                 *     {@link String }
                 *     
                 */
                public void setKontonummer(String value) {
                    this.kontonummer = value;
                }

                /**
                 * Ruft den Wert der betrag-Eigenschaft ab.
                 * 
                 * @return
                 *     possible object is
                 *     {@link String }
                 *     
                 */
                public String getBetrag() {
                    return betrag;
                }

                /**
                 * Legt den Wert der betrag-Eigenschaft fest.
                 * 
                 * @param value
                 *     allowed object is
                 *     {@link String }
                 *     
                 */
                public void setBetrag(String value) {
                    this.betrag = value;
                }

                /**
                 * Ruft den Wert der posLeistungsdatum-Eigenschaft ab.
                 * 
                 * @return
                 *     possible object is
                 *     {@link String }
                 *     
                 */
                public String getPosLeistungsdatum() {
                    return posLeistungsdatum;
                }

                /**
                 * Legt den Wert der posLeistungsdatum-Eigenschaft fest.
                 * 
                 * @param value
                 *     allowed object is
                 *     {@link String }
                 *     
                 */
                public void setPosLeistungsdatum(String value) {
                    this.posLeistungsdatum = value;
                }

                /**
                 * Ruft den Wert der opinfos-Eigenschaft ab.
                 * 
                 * @return
                 *     possible object is
                 *     {@link FibuBelege.FibuBeleg.FibuBelegpositionen.FibuBelegposition.Opinfos }
                 *     
                 */
                public FibuBelege.FibuBeleg.FibuBelegpositionen.FibuBelegposition.Opinfos getOpinfos() {
                    return opinfos;
                }

                /**
                 * Legt den Wert der opinfos-Eigenschaft fest.
                 * 
                 * @param value
                 *     allowed object is
                 *     {@link FibuBelege.FibuBeleg.FibuBelegpositionen.FibuBelegposition.Opinfos }
                 *     
                 */
                public void setOpinfos(FibuBelege.FibuBeleg.FibuBelegpositionen.FibuBelegposition.Opinfos value) {
                    this.opinfos = value;
                }

                /**
                 * Ruft den Wert der steuerschluessel-Eigenschaft ab.
                 * 
                 * @return
                 *     possible object is
                 *     {@link String }
                 *     
                 */
                public String getSteuerschluessel() {
                    return steuerschluessel;
                }

                /**
                 * Legt den Wert der steuerschluessel-Eigenschaft fest.
                 * 
                 * @param value
                 *     allowed object is
                 *     {@link String }
                 *     
                 */
                public void setSteuerschluessel(String value) {
                    this.steuerschluessel = value;
                }


                /**
                 * <p>Java-Klasse für anonymous complex type.
                 * 
                 * <p>Das folgende Schemafragment gibt den erwarteten Content an, der in dieser Klasse enthalten ist.
                 * 
                 * <pre>
                 * &lt;complexType&gt;
                 *   &lt;complexContent&gt;
                 *     &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType"&gt;
                 *       &lt;sequence&gt;
                 *         &lt;element name="OpAngaben"&gt;
                 *           &lt;complexType&gt;
                 *             &lt;complexContent&gt;
                 *               &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType"&gt;
                 *                 &lt;sequence&gt;
                 *                   &lt;element name="opNr" type="{http://www.w3.org/2001/XMLSchema}string"/&gt;
                 *                   &lt;element name="ustIdentNr" type="{http://www.w3.org/2001/XMLSchema}string"/&gt;
                 *                   &lt;element name="opText" type="{http://www.w3.org/2001/XMLSchema}string"/&gt;
                 *                   &lt;element name="verwendungszweck" type="{http://www.w3.org/2001/XMLSchema}string"/&gt;
                 *                   &lt;element name="opBetrag" type="{http://www.w3.org/2001/XMLSchema}string"/&gt;
                 *                 &lt;/sequence&gt;
                 *               &lt;/restriction&gt;
                 *             &lt;/complexContent&gt;
                 *           &lt;/complexType&gt;
                 *         &lt;/element&gt;
                 *       &lt;/sequence&gt;
                 *     &lt;/restriction&gt;
                 *   &lt;/complexContent&gt;
                 * &lt;/complexType&gt;
                 * </pre>
                 * 
                 * 
                 */
                @XmlAccessorType(XmlAccessType.FIELD)
                @XmlType(name = "", propOrder = {
                    "opAngaben"
                })
                public static class Opinfos {

                    @XmlElement(name = "OpAngaben", required = true)
                    protected FibuBelege.FibuBeleg.FibuBelegpositionen.FibuBelegposition.Opinfos.OpAngaben opAngaben;

                    /**
                     * Ruft den Wert der opAngaben-Eigenschaft ab.
                     * 
                     * @return
                     *     possible object is
                     *     {@link FibuBelege.FibuBeleg.FibuBelegpositionen.FibuBelegposition.Opinfos.OpAngaben }
                     *     
                     */
                    public FibuBelege.FibuBeleg.FibuBelegpositionen.FibuBelegposition.Opinfos.OpAngaben getOpAngaben() {
                        return opAngaben;
                    }

                    /**
                     * Legt den Wert der opAngaben-Eigenschaft fest.
                     * 
                     * @param value
                     *     allowed object is
                     *     {@link FibuBelege.FibuBeleg.FibuBelegpositionen.FibuBelegposition.Opinfos.OpAngaben }
                     *     
                     */
                    public void setOpAngaben(FibuBelege.FibuBeleg.FibuBelegpositionen.FibuBelegposition.Opinfos.OpAngaben value) {
                        this.opAngaben = value;
                    }


                    /**
                     * <p>Java-Klasse für anonymous complex type.
                     * 
                     * <p>Das folgende Schemafragment gibt den erwarteten Content an, der in dieser Klasse enthalten ist.
                     * 
                     * <pre>
                     * &lt;complexType&gt;
                     *   &lt;complexContent&gt;
                     *     &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType"&gt;
                     *       &lt;sequence&gt;
                     *         &lt;element name="opNr" type="{http://www.w3.org/2001/XMLSchema}string"/&gt;
                     *         &lt;element name="ustIdentNr" type="{http://www.w3.org/2001/XMLSchema}string"/&gt;
                     *         &lt;element name="opText" type="{http://www.w3.org/2001/XMLSchema}string"/&gt;
                     *         &lt;element name="verwendungszweck" type="{http://www.w3.org/2001/XMLSchema}string"/&gt;
                     *         &lt;element name="opBetrag" type="{http://www.w3.org/2001/XMLSchema}string"/&gt;
                     *       &lt;/sequence&gt;
                     *     &lt;/restriction&gt;
                     *   &lt;/complexContent&gt;
                     * &lt;/complexType&gt;
                     * </pre>
                     * 
                     * 
                     */
                    @XmlAccessorType(XmlAccessType.FIELD)
                    @XmlType(name = "", propOrder = {
                        "opNr",
                        "ustIdentNr",
                        "opText",
                        "verwendungszweck",
                        "opBetrag"
                    })
                    public static class OpAngaben {

                        @XmlElement(required = true)
                        protected String opNr;
                        @XmlElement(required = true)
                        protected String ustIdentNr;
                        @XmlElement(required = true)
                        protected String opText;
                        @XmlElement(required = true)
                        protected String verwendungszweck;
                        @XmlElement(required = true)
                        protected String opBetrag;

                        /**
                         * Ruft den Wert der opNr-Eigenschaft ab.
                         * 
                         * @return
                         *     possible object is
                         *     {@link String }
                         *     
                         */
                        public String getOpNr() {
                            return opNr;
                        }

                        /**
                         * Legt den Wert der opNr-Eigenschaft fest.
                         * 
                         * @param value
                         *     allowed object is
                         *     {@link String }
                         *     
                         */
                        public void setOpNr(String value) {
                            this.opNr = value;
                        }

                        /**
                         * Ruft den Wert der ustIdentNr-Eigenschaft ab.
                         * 
                         * @return
                         *     possible object is
                         *     {@link String }
                         *     
                         */
                        public String getUstIdentNr() {
                            return ustIdentNr;
                        }

                        /**
                         * Legt den Wert der ustIdentNr-Eigenschaft fest.
                         * 
                         * @param value
                         *     allowed object is
                         *     {@link String }
                         *     
                         */
                        public void setUstIdentNr(String value) {
                            this.ustIdentNr = value;
                        }

                        /**
                         * Ruft den Wert der opText-Eigenschaft ab.
                         * 
                         * @return
                         *     possible object is
                         *     {@link String }
                         *     
                         */
                        public String getOpText() {
                            return opText;
                        }

                        /**
                         * Legt den Wert der opText-Eigenschaft fest.
                         * 
                         * @param value
                         *     allowed object is
                         *     {@link String }
                         *     
                         */
                        public void setOpText(String value) {
                            this.opText = value;
                        }

                        /**
                         * Ruft den Wert der verwendungszweck-Eigenschaft ab.
                         * 
                         * @return
                         *     possible object is
                         *     {@link String }
                         *     
                         */
                        public String getVerwendungszweck() {
                            return verwendungszweck;
                        }

                        /**
                         * Legt den Wert der verwendungszweck-Eigenschaft fest.
                         * 
                         * @param value
                         *     allowed object is
                         *     {@link String }
                         *     
                         */
                        public void setVerwendungszweck(String value) {
                            this.verwendungszweck = value;
                        }

                        /**
                         * Ruft den Wert der opBetrag-Eigenschaft ab.
                         * 
                         * @return
                         *     possible object is
                         *     {@link String }
                         *     
                         */
                        public String getOpBetrag() {
                            return opBetrag;
                        }

                        /**
                         * Legt den Wert der opBetrag-Eigenschaft fest.
                         * 
                         * @param value
                         *     allowed object is
                         *     {@link String }
                         *     
                         */
                        public void setOpBetrag(String value) {
                            this.opBetrag = value;
                        }

                    }

                }

            }

        }

    }

}

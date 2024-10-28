//
// Diese Datei wurde mit der JavaTM Architecture for XML Binding(JAXB) Reference Implementation, v2.3.2 generiert 
// Siehe <a href="https://javaee.github.io/jaxb-v2/">https://javaee.github.io/jaxb-v2/</a> 
// Änderungen an dieser Datei gehen bei einer Neukompilierung des Quellschemas verloren. 
// Generiert: 2024.10.28 um 09:01:03 AM CET 
//


package generated;

import java.util.ArrayList;
import java.util.List;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
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
 *         &lt;element name="Personenkonto" maxOccurs="unbounded"&gt;
 *           &lt;complexType&gt;
 *             &lt;complexContent&gt;
 *               &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType"&gt;
 *                 &lt;sequence&gt;
 *                   &lt;element name="kontenart" type="{http://www.w3.org/2001/XMLSchema}string"/&gt;
 *                   &lt;element name="kontonummer" type="{http://www.w3.org/2001/XMLSchema}string"/&gt;
 *                   &lt;element name="bezeichnung" type="{http://www.w3.org/2001/XMLSchema}string"/&gt;
 *                   &lt;element name="Geschaeftspartner"&gt;
 *                     &lt;complexType&gt;
 *                       &lt;complexContent&gt;
 *                         &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType"&gt;
 *                           &lt;sequence&gt;
 *                             &lt;element name="nummer" type="{http://www.w3.org/2001/XMLSchema}string"/&gt;
 *                             &lt;element name="kzJuristischePerson" type="{http://www.w3.org/2001/XMLSchema}string"/&gt;
 *                             &lt;element name="anzeigename" type="{http://www.w3.org/2001/XMLSchema}string"/&gt;
 *                             &lt;element name="Personendaten"&gt;
 *                               &lt;complexType&gt;
 *                                 &lt;complexContent&gt;
 *                                   &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType"&gt;
 *                                     &lt;sequence&gt;
 *                                       &lt;element name="gueltigVon" type="{http://www.w3.org/2001/XMLSchema}string"/&gt;
 *                                       &lt;element name="name1" type="{http://www.w3.org/2001/XMLSchema}string"/&gt;
 *                                       &lt;element name="name2" type="{http://www.w3.org/2001/XMLSchema}string"/&gt;
 *                                     &lt;/sequence&gt;
 *                                   &lt;/restriction&gt;
 *                                 &lt;/complexContent&gt;
 *                               &lt;/complexType&gt;
 *                             &lt;/element&gt;
 *                             &lt;element name="Anschrift"&gt;
 *                               &lt;complexType&gt;
 *                                 &lt;complexContent&gt;
 *                                   &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType"&gt;
 *                                     &lt;sequence&gt;
 *                                       &lt;element name="gueltigVon" type="{http://www.w3.org/2001/XMLSchema}string"/&gt;
 *                                       &lt;element name="name1" type="{http://www.w3.org/2001/XMLSchema}string"/&gt;
 *                                       &lt;element name="name2" type="{http://www.w3.org/2001/XMLSchema}string"/&gt;
 *                                       &lt;element name="strasse" type="{http://www.w3.org/2001/XMLSchema}string"/&gt;
 *                                       &lt;element name="hausnummer" type="{http://www.w3.org/2001/XMLSchema}string"/&gt;
 *                                       &lt;element name="hausnummerZusatz" type="{http://www.w3.org/2001/XMLSchema}string"/&gt;
 *                                       &lt;element name="plz" type="{http://www.w3.org/2001/XMLSchema}string"/&gt;
 *                                       &lt;element name="ort" type="{http://www.w3.org/2001/XMLSchema}string"/&gt;
 *                                       &lt;element name="landkennzeichen" type="{http://www.w3.org/2001/XMLSchema}string"/&gt;
 *                                     &lt;/sequence&gt;
 *                                   &lt;/restriction&gt;
 *                                 &lt;/complexContent&gt;
 *                               &lt;/complexType&gt;
 *                             &lt;/element&gt;
 *                             &lt;element name="TeleKommunikationen"&gt;
 *                               &lt;complexType&gt;
 *                                 &lt;complexContent&gt;
 *                                   &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType"&gt;
 *                                     &lt;sequence&gt;
 *                                       &lt;element name="TeleKommunikation" maxOccurs="unbounded"&gt;
 *                                         &lt;complexType&gt;
 *                                           &lt;complexContent&gt;
 *                                             &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType"&gt;
 *                                               &lt;sequence&gt;
 *                                                 &lt;element name="qualifier" type="{http://www.w3.org/2001/XMLSchema}string"/&gt;
 *                                                 &lt;element name="art" type="{http://www.w3.org/2001/XMLSchema}string"/&gt;
 *                                                 &lt;element name="vorwahl" type="{http://www.w3.org/2001/XMLSchema}string" minOccurs="0"/&gt;
 *                                                 &lt;element name="rufnummer" type="{http://www.w3.org/2001/XMLSchema}string" minOccurs="0"/&gt;
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
 *                             &lt;element name="OnlineKommunikationen"&gt;
 *                               &lt;complexType&gt;
 *                                 &lt;complexContent&gt;
 *                                   &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType"&gt;
 *                                     &lt;sequence&gt;
 *                                       &lt;element name="OnlineKommunikation" maxOccurs="unbounded"&gt;
 *                                         &lt;complexType&gt;
 *                                           &lt;complexContent&gt;
 *                                             &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType"&gt;
 *                                               &lt;sequence&gt;
 *                                                 &lt;element name="qualifier" type="{http://www.w3.org/2001/XMLSchema}string"/&gt;
 *                                                 &lt;element name="art" type="{http://www.w3.org/2001/XMLSchema}string"/&gt;
 *                                                 &lt;element name="email" type="{http://www.w3.org/2001/XMLSchema}string"/&gt;
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
 *                             &lt;element name="Bankverbindungen"&gt;
 *                               &lt;complexType&gt;
 *                                 &lt;complexContent&gt;
 *                                   &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType"&gt;
 *                                     &lt;sequence&gt;
 *                                       &lt;element name="Bankverbindung" maxOccurs="unbounded"&gt;
 *                                         &lt;complexType&gt;
 *                                           &lt;complexContent&gt;
 *                                             &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType"&gt;
 *                                               &lt;sequence&gt;
 *                                                 &lt;element name="qualifier" type="{http://www.w3.org/2001/XMLSchema}string"/&gt;
 *                                                 &lt;element name="hauptbank" type="{http://www.w3.org/2001/XMLSchema}string"/&gt;
 *                                                 &lt;element name="iban" type="{http://www.w3.org/2001/XMLSchema}string"/&gt;
 *                                                 &lt;element name="kontobezeichnung" type="{http://www.w3.org/2001/XMLSchema}string"/&gt;
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
 *                           &lt;/sequence&gt;
 *                         &lt;/restriction&gt;
 *                       &lt;/complexContent&gt;
 *                     &lt;/complexType&gt;
 *                   &lt;/element&gt;
 *                   &lt;element name="festkontoForderung" type="{http://www.w3.org/2001/XMLSchema}string"/&gt;
 *                   &lt;element name="rechnungskonditionNr" type="{http://www.w3.org/2001/XMLSchema}string"/&gt;
 *                   &lt;element name="zahlartNr" type="{http://www.w3.org/2001/XMLSchema}string"/&gt;
 *                 &lt;/sequence&gt;
 *               &lt;/restriction&gt;
 *             &lt;/complexContent&gt;
 *           &lt;/complexType&gt;
 *         &lt;/element&gt;
 *       &lt;/sequence&gt;
 *       &lt;attribute name="objectgroupNr" type="{http://www.w3.org/2001/XMLSchema}string" /&gt;
 *       &lt;attribute name="datumFormat" type="{http://www.w3.org/2001/XMLSchema}string" /&gt;
 *     &lt;/restriction&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "", propOrder = {
    "personenkonto"
})
@XmlRootElement(name = "EGeckoPersonenkonten")
public class EGeckoPersonenkonten {

    @XmlElement(name = "Personenkonto", required = true)
    protected List<EGeckoPersonenkonten.Personenkonto> personenkonto;
    @XmlAttribute(name = "objectgroupNr")
    protected String objectgroupNr;
    @XmlAttribute(name = "datumFormat")
    protected String datumFormat;

    /**
     * Gets the value of the personenkonto property.
     * 
     * <p>
     * This accessor method returns a reference to the live list,
     * not a snapshot. Therefore any modification you make to the
     * returned list will be present inside the JAXB object.
     * This is why there is not a <CODE>set</CODE> method for the personenkonto property.
     * 
     * <p>
     * For example, to add a new item, do as follows:
     * <pre>
     *    getPersonenkonto().add(newItem);
     * </pre>
     * 
     * 
     * <p>
     * Objects of the following type(s) are allowed in the list
     * {@link EGeckoPersonenkonten.Personenkonto }
     * 
     * 
     */
    public List<EGeckoPersonenkonten.Personenkonto> getPersonenkonto() {
        if (personenkonto == null) {
            personenkonto = new ArrayList<EGeckoPersonenkonten.Personenkonto>();
        }
        return this.personenkonto;
    }

    /**
     * Ruft den Wert der objectgroupNr-Eigenschaft ab.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getObjectgroupNr() {
        return objectgroupNr;
    }

    /**
     * Legt den Wert der objectgroupNr-Eigenschaft fest.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setObjectgroupNr(String value) {
        this.objectgroupNr = value;
    }

    /**
     * Ruft den Wert der datumFormat-Eigenschaft ab.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getDatumFormat() {
        return datumFormat;
    }

    /**
     * Legt den Wert der datumFormat-Eigenschaft fest.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setDatumFormat(String value) {
        this.datumFormat = value;
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
     *         &lt;element name="kontenart" type="{http://www.w3.org/2001/XMLSchema}string"/&gt;
     *         &lt;element name="kontonummer" type="{http://www.w3.org/2001/XMLSchema}string"/&gt;
     *         &lt;element name="bezeichnung" type="{http://www.w3.org/2001/XMLSchema}string"/&gt;
     *         &lt;element name="Geschaeftspartner"&gt;
     *           &lt;complexType&gt;
     *             &lt;complexContent&gt;
     *               &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType"&gt;
     *                 &lt;sequence&gt;
     *                   &lt;element name="nummer" type="{http://www.w3.org/2001/XMLSchema}string"/&gt;
     *                   &lt;element name="kzJuristischePerson" type="{http://www.w3.org/2001/XMLSchema}string"/&gt;
     *                   &lt;element name="anzeigename" type="{http://www.w3.org/2001/XMLSchema}string"/&gt;
     *                   &lt;element name="Personendaten"&gt;
     *                     &lt;complexType&gt;
     *                       &lt;complexContent&gt;
     *                         &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType"&gt;
     *                           &lt;sequence&gt;
     *                             &lt;element name="gueltigVon" type="{http://www.w3.org/2001/XMLSchema}string"/&gt;
     *                             &lt;element name="name1" type="{http://www.w3.org/2001/XMLSchema}string"/&gt;
     *                             &lt;element name="name2" type="{http://www.w3.org/2001/XMLSchema}string"/&gt;
     *                           &lt;/sequence&gt;
     *                         &lt;/restriction&gt;
     *                       &lt;/complexContent&gt;
     *                     &lt;/complexType&gt;
     *                   &lt;/element&gt;
     *                   &lt;element name="Anschrift"&gt;
     *                     &lt;complexType&gt;
     *                       &lt;complexContent&gt;
     *                         &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType"&gt;
     *                           &lt;sequence&gt;
     *                             &lt;element name="gueltigVon" type="{http://www.w3.org/2001/XMLSchema}string"/&gt;
     *                             &lt;element name="name1" type="{http://www.w3.org/2001/XMLSchema}string"/&gt;
     *                             &lt;element name="name2" type="{http://www.w3.org/2001/XMLSchema}string"/&gt;
     *                             &lt;element name="strasse" type="{http://www.w3.org/2001/XMLSchema}string"/&gt;
     *                             &lt;element name="hausnummer" type="{http://www.w3.org/2001/XMLSchema}string"/&gt;
     *                             &lt;element name="hausnummerZusatz" type="{http://www.w3.org/2001/XMLSchema}string"/&gt;
     *                             &lt;element name="plz" type="{http://www.w3.org/2001/XMLSchema}string"/&gt;
     *                             &lt;element name="ort" type="{http://www.w3.org/2001/XMLSchema}string"/&gt;
     *                             &lt;element name="landkennzeichen" type="{http://www.w3.org/2001/XMLSchema}string"/&gt;
     *                           &lt;/sequence&gt;
     *                         &lt;/restriction&gt;
     *                       &lt;/complexContent&gt;
     *                     &lt;/complexType&gt;
     *                   &lt;/element&gt;
     *                   &lt;element name="TeleKommunikationen"&gt;
     *                     &lt;complexType&gt;
     *                       &lt;complexContent&gt;
     *                         &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType"&gt;
     *                           &lt;sequence&gt;
     *                             &lt;element name="TeleKommunikation" maxOccurs="unbounded"&gt;
     *                               &lt;complexType&gt;
     *                                 &lt;complexContent&gt;
     *                                   &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType"&gt;
     *                                     &lt;sequence&gt;
     *                                       &lt;element name="qualifier" type="{http://www.w3.org/2001/XMLSchema}string"/&gt;
     *                                       &lt;element name="art" type="{http://www.w3.org/2001/XMLSchema}string"/&gt;
     *                                       &lt;element name="vorwahl" type="{http://www.w3.org/2001/XMLSchema}string" minOccurs="0"/&gt;
     *                                       &lt;element name="rufnummer" type="{http://www.w3.org/2001/XMLSchema}string" minOccurs="0"/&gt;
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
     *                   &lt;element name="OnlineKommunikationen"&gt;
     *                     &lt;complexType&gt;
     *                       &lt;complexContent&gt;
     *                         &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType"&gt;
     *                           &lt;sequence&gt;
     *                             &lt;element name="OnlineKommunikation" maxOccurs="unbounded"&gt;
     *                               &lt;complexType&gt;
     *                                 &lt;complexContent&gt;
     *                                   &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType"&gt;
     *                                     &lt;sequence&gt;
     *                                       &lt;element name="qualifier" type="{http://www.w3.org/2001/XMLSchema}string"/&gt;
     *                                       &lt;element name="art" type="{http://www.w3.org/2001/XMLSchema}string"/&gt;
     *                                       &lt;element name="email" type="{http://www.w3.org/2001/XMLSchema}string"/&gt;
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
     *                   &lt;element name="Bankverbindungen"&gt;
     *                     &lt;complexType&gt;
     *                       &lt;complexContent&gt;
     *                         &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType"&gt;
     *                           &lt;sequence&gt;
     *                             &lt;element name="Bankverbindung" maxOccurs="unbounded"&gt;
     *                               &lt;complexType&gt;
     *                                 &lt;complexContent&gt;
     *                                   &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType"&gt;
     *                                     &lt;sequence&gt;
     *                                       &lt;element name="qualifier" type="{http://www.w3.org/2001/XMLSchema}string"/&gt;
     *                                       &lt;element name="hauptbank" type="{http://www.w3.org/2001/XMLSchema}string"/&gt;
     *                                       &lt;element name="iban" type="{http://www.w3.org/2001/XMLSchema}string"/&gt;
     *                                       &lt;element name="kontobezeichnung" type="{http://www.w3.org/2001/XMLSchema}string"/&gt;
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
     *         &lt;element name="festkontoForderung" type="{http://www.w3.org/2001/XMLSchema}string"/&gt;
     *         &lt;element name="rechnungskonditionNr" type="{http://www.w3.org/2001/XMLSchema}string"/&gt;
     *         &lt;element name="zahlartNr" type="{http://www.w3.org/2001/XMLSchema}string"/&gt;
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
        "kontenart",
        "kontonummer",
        "bezeichnung",
        "geschaeftspartner",
        "festkontoForderung",
        "rechnungskonditionNr",
        "zahlartNr"
    })
    public static class Personenkonto {

        @XmlElement(required = true)
        protected String kontenart;
        @XmlElement(required = true)
        protected String kontonummer;
        @XmlElement(required = true)
        protected String bezeichnung;
        @XmlElement(name = "Geschaeftspartner", required = true)
        protected EGeckoPersonenkonten.Personenkonto.Geschaeftspartner geschaeftspartner;
        @XmlElement(required = true)
        protected String festkontoForderung;
        @XmlElement(required = true)
        protected String rechnungskonditionNr;
        @XmlElement(required = true)
        protected String zahlartNr;

        /**
         * Ruft den Wert der kontenart-Eigenschaft ab.
         * 
         * @return
         *     possible object is
         *     {@link String }
         *     
         */
        public String getKontenart() {
            return kontenart;
        }

        /**
         * Legt den Wert der kontenart-Eigenschaft fest.
         * 
         * @param value
         *     allowed object is
         *     {@link String }
         *     
         */
        public void setKontenart(String value) {
            this.kontenart = value;
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
         * Ruft den Wert der bezeichnung-Eigenschaft ab.
         * 
         * @return
         *     possible object is
         *     {@link String }
         *     
         */
        public String getBezeichnung() {
            return bezeichnung;
        }

        /**
         * Legt den Wert der bezeichnung-Eigenschaft fest.
         * 
         * @param value
         *     allowed object is
         *     {@link String }
         *     
         */
        public void setBezeichnung(String value) {
            this.bezeichnung = value;
        }

        /**
         * Ruft den Wert der geschaeftspartner-Eigenschaft ab.
         * 
         * @return
         *     possible object is
         *     {@link EGeckoPersonenkonten.Personenkonto.Geschaeftspartner }
         *     
         */
        public EGeckoPersonenkonten.Personenkonto.Geschaeftspartner getGeschaeftspartner() {
            return geschaeftspartner;
        }

        /**
         * Legt den Wert der geschaeftspartner-Eigenschaft fest.
         * 
         * @param value
         *     allowed object is
         *     {@link EGeckoPersonenkonten.Personenkonto.Geschaeftspartner }
         *     
         */
        public void setGeschaeftspartner(EGeckoPersonenkonten.Personenkonto.Geschaeftspartner value) {
            this.geschaeftspartner = value;
        }

        /**
         * Ruft den Wert der festkontoForderung-Eigenschaft ab.
         * 
         * @return
         *     possible object is
         *     {@link String }
         *     
         */
        public String getFestkontoForderung() {
            return festkontoForderung;
        }

        /**
         * Legt den Wert der festkontoForderung-Eigenschaft fest.
         * 
         * @param value
         *     allowed object is
         *     {@link String }
         *     
         */
        public void setFestkontoForderung(String value) {
            this.festkontoForderung = value;
        }

        /**
         * Ruft den Wert der rechnungskonditionNr-Eigenschaft ab.
         * 
         * @return
         *     possible object is
         *     {@link String }
         *     
         */
        public String getRechnungskonditionNr() {
            return rechnungskonditionNr;
        }

        /**
         * Legt den Wert der rechnungskonditionNr-Eigenschaft fest.
         * 
         * @param value
         *     allowed object is
         *     {@link String }
         *     
         */
        public void setRechnungskonditionNr(String value) {
            this.rechnungskonditionNr = value;
        }

        /**
         * Ruft den Wert der zahlartNr-Eigenschaft ab.
         * 
         * @return
         *     possible object is
         *     {@link String }
         *     
         */
        public String getZahlartNr() {
            return zahlartNr;
        }

        /**
         * Legt den Wert der zahlartNr-Eigenschaft fest.
         * 
         * @param value
         *     allowed object is
         *     {@link String }
         *     
         */
        public void setZahlartNr(String value) {
            this.zahlartNr = value;
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
         *         &lt;element name="nummer" type="{http://www.w3.org/2001/XMLSchema}string"/&gt;
         *         &lt;element name="kzJuristischePerson" type="{http://www.w3.org/2001/XMLSchema}string"/&gt;
         *         &lt;element name="anzeigename" type="{http://www.w3.org/2001/XMLSchema}string"/&gt;
         *         &lt;element name="Personendaten"&gt;
         *           &lt;complexType&gt;
         *             &lt;complexContent&gt;
         *               &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType"&gt;
         *                 &lt;sequence&gt;
         *                   &lt;element name="gueltigVon" type="{http://www.w3.org/2001/XMLSchema}string"/&gt;
         *                   &lt;element name="name1" type="{http://www.w3.org/2001/XMLSchema}string"/&gt;
         *                   &lt;element name="name2" type="{http://www.w3.org/2001/XMLSchema}string"/&gt;
         *                 &lt;/sequence&gt;
         *               &lt;/restriction&gt;
         *             &lt;/complexContent&gt;
         *           &lt;/complexType&gt;
         *         &lt;/element&gt;
         *         &lt;element name="Anschrift"&gt;
         *           &lt;complexType&gt;
         *             &lt;complexContent&gt;
         *               &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType"&gt;
         *                 &lt;sequence&gt;
         *                   &lt;element name="gueltigVon" type="{http://www.w3.org/2001/XMLSchema}string"/&gt;
         *                   &lt;element name="name1" type="{http://www.w3.org/2001/XMLSchema}string"/&gt;
         *                   &lt;element name="name2" type="{http://www.w3.org/2001/XMLSchema}string"/&gt;
         *                   &lt;element name="strasse" type="{http://www.w3.org/2001/XMLSchema}string"/&gt;
         *                   &lt;element name="hausnummer" type="{http://www.w3.org/2001/XMLSchema}string"/&gt;
         *                   &lt;element name="hausnummerZusatz" type="{http://www.w3.org/2001/XMLSchema}string"/&gt;
         *                   &lt;element name="plz" type="{http://www.w3.org/2001/XMLSchema}string"/&gt;
         *                   &lt;element name="ort" type="{http://www.w3.org/2001/XMLSchema}string"/&gt;
         *                   &lt;element name="landkennzeichen" type="{http://www.w3.org/2001/XMLSchema}string"/&gt;
         *                 &lt;/sequence&gt;
         *               &lt;/restriction&gt;
         *             &lt;/complexContent&gt;
         *           &lt;/complexType&gt;
         *         &lt;/element&gt;
         *         &lt;element name="TeleKommunikationen"&gt;
         *           &lt;complexType&gt;
         *             &lt;complexContent&gt;
         *               &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType"&gt;
         *                 &lt;sequence&gt;
         *                   &lt;element name="TeleKommunikation" maxOccurs="unbounded"&gt;
         *                     &lt;complexType&gt;
         *                       &lt;complexContent&gt;
         *                         &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType"&gt;
         *                           &lt;sequence&gt;
         *                             &lt;element name="qualifier" type="{http://www.w3.org/2001/XMLSchema}string"/&gt;
         *                             &lt;element name="art" type="{http://www.w3.org/2001/XMLSchema}string"/&gt;
         *                             &lt;element name="vorwahl" type="{http://www.w3.org/2001/XMLSchema}string" minOccurs="0"/&gt;
         *                             &lt;element name="rufnummer" type="{http://www.w3.org/2001/XMLSchema}string" minOccurs="0"/&gt;
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
         *         &lt;element name="OnlineKommunikationen"&gt;
         *           &lt;complexType&gt;
         *             &lt;complexContent&gt;
         *               &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType"&gt;
         *                 &lt;sequence&gt;
         *                   &lt;element name="OnlineKommunikation" maxOccurs="unbounded"&gt;
         *                     &lt;complexType&gt;
         *                       &lt;complexContent&gt;
         *                         &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType"&gt;
         *                           &lt;sequence&gt;
         *                             &lt;element name="qualifier" type="{http://www.w3.org/2001/XMLSchema}string"/&gt;
         *                             &lt;element name="art" type="{http://www.w3.org/2001/XMLSchema}string"/&gt;
         *                             &lt;element name="email" type="{http://www.w3.org/2001/XMLSchema}string"/&gt;
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
         *         &lt;element name="Bankverbindungen"&gt;
         *           &lt;complexType&gt;
         *             &lt;complexContent&gt;
         *               &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType"&gt;
         *                 &lt;sequence&gt;
         *                   &lt;element name="Bankverbindung" maxOccurs="unbounded"&gt;
         *                     &lt;complexType&gt;
         *                       &lt;complexContent&gt;
         *                         &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType"&gt;
         *                           &lt;sequence&gt;
         *                             &lt;element name="qualifier" type="{http://www.w3.org/2001/XMLSchema}string"/&gt;
         *                             &lt;element name="hauptbank" type="{http://www.w3.org/2001/XMLSchema}string"/&gt;
         *                             &lt;element name="iban" type="{http://www.w3.org/2001/XMLSchema}string"/&gt;
         *                             &lt;element name="kontobezeichnung" type="{http://www.w3.org/2001/XMLSchema}string"/&gt;
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
            "nummer",
            "kzJuristischePerson",
            "anzeigename",
            "personendaten",
            "anschrift",
            "teleKommunikationen",
            "onlineKommunikationen",
            "bankverbindungen"
        })
        public static class Geschaeftspartner {

            @XmlElement(required = true)
            protected String nummer;
            @XmlElement(required = true)
            protected String kzJuristischePerson;
            @XmlElement(required = true)
            protected String anzeigename;
            @XmlElement(name = "Personendaten", required = true)
            protected EGeckoPersonenkonten.Personenkonto.Geschaeftspartner.Personendaten personendaten;
            @XmlElement(name = "Anschrift", required = true)
            protected EGeckoPersonenkonten.Personenkonto.Geschaeftspartner.Anschrift anschrift;
            @XmlElement(name = "TeleKommunikationen", required = true)
            protected EGeckoPersonenkonten.Personenkonto.Geschaeftspartner.TeleKommunikationen teleKommunikationen;
            @XmlElement(name = "OnlineKommunikationen", required = true)
            protected EGeckoPersonenkonten.Personenkonto.Geschaeftspartner.OnlineKommunikationen onlineKommunikationen;
            @XmlElement(name = "Bankverbindungen", required = true)
            protected EGeckoPersonenkonten.Personenkonto.Geschaeftspartner.Bankverbindungen bankverbindungen;

            /**
             * Ruft den Wert der nummer-Eigenschaft ab.
             * 
             * @return
             *     possible object is
             *     {@link String }
             *     
             */
            public String getNummer() {
                return nummer;
            }

            /**
             * Legt den Wert der nummer-Eigenschaft fest.
             * 
             * @param value
             *     allowed object is
             *     {@link String }
             *     
             */
            public void setNummer(String value) {
                this.nummer = value;
            }

            /**
             * Ruft den Wert der kzJuristischePerson-Eigenschaft ab.
             * 
             * @return
             *     possible object is
             *     {@link String }
             *     
             */
            public String getKzJuristischePerson() {
                return kzJuristischePerson;
            }

            /**
             * Legt den Wert der kzJuristischePerson-Eigenschaft fest.
             * 
             * @param value
             *     allowed object is
             *     {@link String }
             *     
             */
            public void setKzJuristischePerson(String value) {
                this.kzJuristischePerson = value;
            }

            /**
             * Ruft den Wert der anzeigename-Eigenschaft ab.
             * 
             * @return
             *     possible object is
             *     {@link String }
             *     
             */
            public String getAnzeigename() {
                return anzeigename;
            }

            /**
             * Legt den Wert der anzeigename-Eigenschaft fest.
             * 
             * @param value
             *     allowed object is
             *     {@link String }
             *     
             */
            public void setAnzeigename(String value) {
                this.anzeigename = value;
            }

            /**
             * Ruft den Wert der personendaten-Eigenschaft ab.
             * 
             * @return
             *     possible object is
             *     {@link EGeckoPersonenkonten.Personenkonto.Geschaeftspartner.Personendaten }
             *     
             */
            public EGeckoPersonenkonten.Personenkonto.Geschaeftspartner.Personendaten getPersonendaten() {
                return personendaten;
            }

            /**
             * Legt den Wert der personendaten-Eigenschaft fest.
             * 
             * @param value
             *     allowed object is
             *     {@link EGeckoPersonenkonten.Personenkonto.Geschaeftspartner.Personendaten }
             *     
             */
            public void setPersonendaten(EGeckoPersonenkonten.Personenkonto.Geschaeftspartner.Personendaten value) {
                this.personendaten = value;
            }

            /**
             * Ruft den Wert der anschrift-Eigenschaft ab.
             * 
             * @return
             *     possible object is
             *     {@link EGeckoPersonenkonten.Personenkonto.Geschaeftspartner.Anschrift }
             *     
             */
            public EGeckoPersonenkonten.Personenkonto.Geschaeftspartner.Anschrift getAnschrift() {
                return anschrift;
            }

            /**
             * Legt den Wert der anschrift-Eigenschaft fest.
             * 
             * @param value
             *     allowed object is
             *     {@link EGeckoPersonenkonten.Personenkonto.Geschaeftspartner.Anschrift }
             *     
             */
            public void setAnschrift(EGeckoPersonenkonten.Personenkonto.Geschaeftspartner.Anschrift value) {
                this.anschrift = value;
            }

            /**
             * Ruft den Wert der teleKommunikationen-Eigenschaft ab.
             * 
             * @return
             *     possible object is
             *     {@link EGeckoPersonenkonten.Personenkonto.Geschaeftspartner.TeleKommunikationen }
             *     
             */
            public EGeckoPersonenkonten.Personenkonto.Geschaeftspartner.TeleKommunikationen getTeleKommunikationen() {
                return teleKommunikationen;
            }

            /**
             * Legt den Wert der teleKommunikationen-Eigenschaft fest.
             * 
             * @param value
             *     allowed object is
             *     {@link EGeckoPersonenkonten.Personenkonto.Geschaeftspartner.TeleKommunikationen }
             *     
             */
            public void setTeleKommunikationen(EGeckoPersonenkonten.Personenkonto.Geschaeftspartner.TeleKommunikationen value) {
                this.teleKommunikationen = value;
            }

            /**
             * Ruft den Wert der onlineKommunikationen-Eigenschaft ab.
             * 
             * @return
             *     possible object is
             *     {@link EGeckoPersonenkonten.Personenkonto.Geschaeftspartner.OnlineKommunikationen }
             *     
             */
            public EGeckoPersonenkonten.Personenkonto.Geschaeftspartner.OnlineKommunikationen getOnlineKommunikationen() {
                return onlineKommunikationen;
            }

            /**
             * Legt den Wert der onlineKommunikationen-Eigenschaft fest.
             * 
             * @param value
             *     allowed object is
             *     {@link EGeckoPersonenkonten.Personenkonto.Geschaeftspartner.OnlineKommunikationen }
             *     
             */
            public void setOnlineKommunikationen(EGeckoPersonenkonten.Personenkonto.Geschaeftspartner.OnlineKommunikationen value) {
                this.onlineKommunikationen = value;
            }

            /**
             * Ruft den Wert der bankverbindungen-Eigenschaft ab.
             * 
             * @return
             *     possible object is
             *     {@link EGeckoPersonenkonten.Personenkonto.Geschaeftspartner.Bankverbindungen }
             *     
             */
            public EGeckoPersonenkonten.Personenkonto.Geschaeftspartner.Bankverbindungen getBankverbindungen() {
                return bankverbindungen;
            }

            /**
             * Legt den Wert der bankverbindungen-Eigenschaft fest.
             * 
             * @param value
             *     allowed object is
             *     {@link EGeckoPersonenkonten.Personenkonto.Geschaeftspartner.Bankverbindungen }
             *     
             */
            public void setBankverbindungen(EGeckoPersonenkonten.Personenkonto.Geschaeftspartner.Bankverbindungen value) {
                this.bankverbindungen = value;
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
             *         &lt;element name="gueltigVon" type="{http://www.w3.org/2001/XMLSchema}string"/&gt;
             *         &lt;element name="name1" type="{http://www.w3.org/2001/XMLSchema}string"/&gt;
             *         &lt;element name="name2" type="{http://www.w3.org/2001/XMLSchema}string"/&gt;
             *         &lt;element name="strasse" type="{http://www.w3.org/2001/XMLSchema}string"/&gt;
             *         &lt;element name="hausnummer" type="{http://www.w3.org/2001/XMLSchema}string"/&gt;
             *         &lt;element name="hausnummerZusatz" type="{http://www.w3.org/2001/XMLSchema}string"/&gt;
             *         &lt;element name="plz" type="{http://www.w3.org/2001/XMLSchema}string"/&gt;
             *         &lt;element name="ort" type="{http://www.w3.org/2001/XMLSchema}string"/&gt;
             *         &lt;element name="landkennzeichen" type="{http://www.w3.org/2001/XMLSchema}string"/&gt;
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
                "gueltigVon",
                "name1",
                "name2",
                "strasse",
                "hausnummer",
                "hausnummerZusatz",
                "plz",
                "ort",
                "landkennzeichen"
            })
            public static class Anschrift {

                @XmlElement(required = true)
                protected String gueltigVon;
                @XmlElement(required = true)
                protected String name1;
                @XmlElement(required = true)
                protected String name2;
                @XmlElement(required = true)
                protected String strasse;
                @XmlElement(required = true)
                protected String hausnummer;
                @XmlElement(required = true)
                protected String hausnummerZusatz;
                @XmlElement(required = true)
                protected String plz;
                @XmlElement(required = true)
                protected String ort;
                @XmlElement(required = true)
                protected String landkennzeichen;

                /**
                 * Ruft den Wert der gueltigVon-Eigenschaft ab.
                 * 
                 * @return
                 *     possible object is
                 *     {@link String }
                 *     
                 */
                public String getGueltigVon() {
                    return gueltigVon;
                }

                /**
                 * Legt den Wert der gueltigVon-Eigenschaft fest.
                 * 
                 * @param value
                 *     allowed object is
                 *     {@link String }
                 *     
                 */
                public void setGueltigVon(String value) {
                    this.gueltigVon = value;
                }

                /**
                 * Ruft den Wert der name1-Eigenschaft ab.
                 * 
                 * @return
                 *     possible object is
                 *     {@link String }
                 *     
                 */
                public String getName1() {
                    return name1;
                }

                /**
                 * Legt den Wert der name1-Eigenschaft fest.
                 * 
                 * @param value
                 *     allowed object is
                 *     {@link String }
                 *     
                 */
                public void setName1(String value) {
                    this.name1 = value;
                }

                /**
                 * Ruft den Wert der name2-Eigenschaft ab.
                 * 
                 * @return
                 *     possible object is
                 *     {@link String }
                 *     
                 */
                public String getName2() {
                    return name2;
                }

                /**
                 * Legt den Wert der name2-Eigenschaft fest.
                 * 
                 * @param value
                 *     allowed object is
                 *     {@link String }
                 *     
                 */
                public void setName2(String value) {
                    this.name2 = value;
                }

                /**
                 * Ruft den Wert der strasse-Eigenschaft ab.
                 * 
                 * @return
                 *     possible object is
                 *     {@link String }
                 *     
                 */
                public String getStrasse() {
                    return strasse;
                }

                /**
                 * Legt den Wert der strasse-Eigenschaft fest.
                 * 
                 * @param value
                 *     allowed object is
                 *     {@link String }
                 *     
                 */
                public void setStrasse(String value) {
                    this.strasse = value;
                }

                /**
                 * Ruft den Wert der hausnummer-Eigenschaft ab.
                 * 
                 * @return
                 *     possible object is
                 *     {@link String }
                 *     
                 */
                public String getHausnummer() {
                    return hausnummer;
                }

                /**
                 * Legt den Wert der hausnummer-Eigenschaft fest.
                 * 
                 * @param value
                 *     allowed object is
                 *     {@link String }
                 *     
                 */
                public void setHausnummer(String value) {
                    this.hausnummer = value;
                }

                /**
                 * Ruft den Wert der hausnummerZusatz-Eigenschaft ab.
                 * 
                 * @return
                 *     possible object is
                 *     {@link String }
                 *     
                 */
                public String getHausnummerZusatz() {
                    return hausnummerZusatz;
                }

                /**
                 * Legt den Wert der hausnummerZusatz-Eigenschaft fest.
                 * 
                 * @param value
                 *     allowed object is
                 *     {@link String }
                 *     
                 */
                public void setHausnummerZusatz(String value) {
                    this.hausnummerZusatz = value;
                }

                /**
                 * Ruft den Wert der plz-Eigenschaft ab.
                 * 
                 * @return
                 *     possible object is
                 *     {@link String }
                 *     
                 */
                public String getPlz() {
                    return plz;
                }

                /**
                 * Legt den Wert der plz-Eigenschaft fest.
                 * 
                 * @param value
                 *     allowed object is
                 *     {@link String }
                 *     
                 */
                public void setPlz(String value) {
                    this.plz = value;
                }

                /**
                 * Ruft den Wert der ort-Eigenschaft ab.
                 * 
                 * @return
                 *     possible object is
                 *     {@link String }
                 *     
                 */
                public String getOrt() {
                    return ort;
                }

                /**
                 * Legt den Wert der ort-Eigenschaft fest.
                 * 
                 * @param value
                 *     allowed object is
                 *     {@link String }
                 *     
                 */
                public void setOrt(String value) {
                    this.ort = value;
                }

                /**
                 * Ruft den Wert der landkennzeichen-Eigenschaft ab.
                 * 
                 * @return
                 *     possible object is
                 *     {@link String }
                 *     
                 */
                public String getLandkennzeichen() {
                    return landkennzeichen;
                }

                /**
                 * Legt den Wert der landkennzeichen-Eigenschaft fest.
                 * 
                 * @param value
                 *     allowed object is
                 *     {@link String }
                 *     
                 */
                public void setLandkennzeichen(String value) {
                    this.landkennzeichen = value;
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
             *         &lt;element name="Bankverbindung" maxOccurs="unbounded"&gt;
             *           &lt;complexType&gt;
             *             &lt;complexContent&gt;
             *               &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType"&gt;
             *                 &lt;sequence&gt;
             *                   &lt;element name="qualifier" type="{http://www.w3.org/2001/XMLSchema}string"/&gt;
             *                   &lt;element name="hauptbank" type="{http://www.w3.org/2001/XMLSchema}string"/&gt;
             *                   &lt;element name="iban" type="{http://www.w3.org/2001/XMLSchema}string"/&gt;
             *                   &lt;element name="kontobezeichnung" type="{http://www.w3.org/2001/XMLSchema}string"/&gt;
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
                "bankverbindung"
            })
            public static class Bankverbindungen {

                @XmlElement(name = "Bankverbindung", required = true)
                protected List<EGeckoPersonenkonten.Personenkonto.Geschaeftspartner.Bankverbindungen.Bankverbindung> bankverbindung;

                /**
                 * Gets the value of the bankverbindung property.
                 * 
                 * <p>
                 * This accessor method returns a reference to the live list,
                 * not a snapshot. Therefore any modification you make to the
                 * returned list will be present inside the JAXB object.
                 * This is why there is not a <CODE>set</CODE> method for the bankverbindung property.
                 * 
                 * <p>
                 * For example, to add a new item, do as follows:
                 * <pre>
                 *    getBankverbindung().add(newItem);
                 * </pre>
                 * 
                 * 
                 * <p>
                 * Objects of the following type(s) are allowed in the list
                 * {@link EGeckoPersonenkonten.Personenkonto.Geschaeftspartner.Bankverbindungen.Bankverbindung }
                 * 
                 * 
                 */
                public List<EGeckoPersonenkonten.Personenkonto.Geschaeftspartner.Bankverbindungen.Bankverbindung> getBankverbindung() {
                    if (bankverbindung == null) {
                        bankverbindung = new ArrayList<EGeckoPersonenkonten.Personenkonto.Geschaeftspartner.Bankverbindungen.Bankverbindung>();
                    }
                    return this.bankverbindung;
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
                 *         &lt;element name="qualifier" type="{http://www.w3.org/2001/XMLSchema}string"/&gt;
                 *         &lt;element name="hauptbank" type="{http://www.w3.org/2001/XMLSchema}string"/&gt;
                 *         &lt;element name="iban" type="{http://www.w3.org/2001/XMLSchema}string"/&gt;
                 *         &lt;element name="kontobezeichnung" type="{http://www.w3.org/2001/XMLSchema}string"/&gt;
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
                    "qualifier",
                    "hauptbank",
                    "iban",
                    "kontobezeichnung"
                })
                public static class Bankverbindung {

                    @XmlElement(required = true)
                    protected String qualifier;
                    @XmlElement(required = true)
                    protected String hauptbank;
                    @XmlElement(required = true)
                    protected String iban;
                    @XmlElement(required = true)
                    protected String kontobezeichnung;

                    /**
                     * Ruft den Wert der qualifier-Eigenschaft ab.
                     * 
                     * @return
                     *     possible object is
                     *     {@link String }
                     *     
                     */
                    public String getQualifier() {
                        return qualifier;
                    }

                    /**
                     * Legt den Wert der qualifier-Eigenschaft fest.
                     * 
                     * @param value
                     *     allowed object is
                     *     {@link String }
                     *     
                     */
                    public void setQualifier(String value) {
                        this.qualifier = value;
                    }

                    /**
                     * Ruft den Wert der hauptbank-Eigenschaft ab.
                     * 
                     * @return
                     *     possible object is
                     *     {@link String }
                     *     
                     */
                    public String getHauptbank() {
                        return hauptbank;
                    }

                    /**
                     * Legt den Wert der hauptbank-Eigenschaft fest.
                     * 
                     * @param value
                     *     allowed object is
                     *     {@link String }
                     *     
                     */
                    public void setHauptbank(String value) {
                        this.hauptbank = value;
                    }

                    /**
                     * Ruft den Wert der iban-Eigenschaft ab.
                     * 
                     * @return
                     *     possible object is
                     *     {@link String }
                     *     
                     */
                    public String getIban() {
                        return iban;
                    }

                    /**
                     * Legt den Wert der iban-Eigenschaft fest.
                     * 
                     * @param value
                     *     allowed object is
                     *     {@link String }
                     *     
                     */
                    public void setIban(String value) {
                        this.iban = value;
                    }

                    /**
                     * Ruft den Wert der kontobezeichnung-Eigenschaft ab.
                     * 
                     * @return
                     *     possible object is
                     *     {@link String }
                     *     
                     */
                    public String getKontobezeichnung() {
                        return kontobezeichnung;
                    }

                    /**
                     * Legt den Wert der kontobezeichnung-Eigenschaft fest.
                     * 
                     * @param value
                     *     allowed object is
                     *     {@link String }
                     *     
                     */
                    public void setKontobezeichnung(String value) {
                        this.kontobezeichnung = value;
                    }

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
             *         &lt;element name="OnlineKommunikation" maxOccurs="unbounded"&gt;
             *           &lt;complexType&gt;
             *             &lt;complexContent&gt;
             *               &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType"&gt;
             *                 &lt;sequence&gt;
             *                   &lt;element name="qualifier" type="{http://www.w3.org/2001/XMLSchema}string"/&gt;
             *                   &lt;element name="art" type="{http://www.w3.org/2001/XMLSchema}string"/&gt;
             *                   &lt;element name="email" type="{http://www.w3.org/2001/XMLSchema}string"/&gt;
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
                "onlineKommunikation"
            })
            public static class OnlineKommunikationen {

                @XmlElement(name = "OnlineKommunikation", required = true)
                protected List<EGeckoPersonenkonten.Personenkonto.Geschaeftspartner.OnlineKommunikationen.OnlineKommunikation> onlineKommunikation;

                /**
                 * Gets the value of the onlineKommunikation property.
                 * 
                 * <p>
                 * This accessor method returns a reference to the live list,
                 * not a snapshot. Therefore any modification you make to the
                 * returned list will be present inside the JAXB object.
                 * This is why there is not a <CODE>set</CODE> method for the onlineKommunikation property.
                 * 
                 * <p>
                 * For example, to add a new item, do as follows:
                 * <pre>
                 *    getOnlineKommunikation().add(newItem);
                 * </pre>
                 * 
                 * 
                 * <p>
                 * Objects of the following type(s) are allowed in the list
                 * {@link EGeckoPersonenkonten.Personenkonto.Geschaeftspartner.OnlineKommunikationen.OnlineKommunikation }
                 * 
                 * 
                 */
                public List<EGeckoPersonenkonten.Personenkonto.Geschaeftspartner.OnlineKommunikationen.OnlineKommunikation> getOnlineKommunikation() {
                    if (onlineKommunikation == null) {
                        onlineKommunikation = new ArrayList<EGeckoPersonenkonten.Personenkonto.Geschaeftspartner.OnlineKommunikationen.OnlineKommunikation>();
                    }
                    return this.onlineKommunikation;
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
                 *         &lt;element name="qualifier" type="{http://www.w3.org/2001/XMLSchema}string"/&gt;
                 *         &lt;element name="art" type="{http://www.w3.org/2001/XMLSchema}string"/&gt;
                 *         &lt;element name="email" type="{http://www.w3.org/2001/XMLSchema}string"/&gt;
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
                    "qualifier",
                    "art",
                    "email"
                })
                public static class OnlineKommunikation {

                    @XmlElement(required = true)
                    protected String qualifier;
                    @XmlElement(required = true)
                    protected String art;
                    @XmlElement(required = true)
                    protected String email;

                    /**
                     * Ruft den Wert der qualifier-Eigenschaft ab.
                     * 
                     * @return
                     *     possible object is
                     *     {@link String }
                     *     
                     */
                    public String getQualifier() {
                        return qualifier;
                    }

                    /**
                     * Legt den Wert der qualifier-Eigenschaft fest.
                     * 
                     * @param value
                     *     allowed object is
                     *     {@link String }
                     *     
                     */
                    public void setQualifier(String value) {
                        this.qualifier = value;
                    }

                    /**
                     * Ruft den Wert der art-Eigenschaft ab.
                     * 
                     * @return
                     *     possible object is
                     *     {@link String }
                     *     
                     */
                    public String getArt() {
                        return art;
                    }

                    /**
                     * Legt den Wert der art-Eigenschaft fest.
                     * 
                     * @param value
                     *     allowed object is
                     *     {@link String }
                     *     
                     */
                    public void setArt(String value) {
                        this.art = value;
                    }

                    /**
                     * Ruft den Wert der email-Eigenschaft ab.
                     * 
                     * @return
                     *     possible object is
                     *     {@link String }
                     *     
                     */
                    public String getEmail() {
                        return email;
                    }

                    /**
                     * Legt den Wert der email-Eigenschaft fest.
                     * 
                     * @param value
                     *     allowed object is
                     *     {@link String }
                     *     
                     */
                    public void setEmail(String value) {
                        this.email = value;
                    }

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
             *         &lt;element name="gueltigVon" type="{http://www.w3.org/2001/XMLSchema}string"/&gt;
             *         &lt;element name="name1" type="{http://www.w3.org/2001/XMLSchema}string"/&gt;
             *         &lt;element name="name2" type="{http://www.w3.org/2001/XMLSchema}string"/&gt;
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
                "gueltigVon",
                "name1",
                "name2"
            })
            public static class Personendaten {

                @XmlElement(required = true)
                protected String gueltigVon;
                @XmlElement(required = true)
                protected String name1;
                @XmlElement(required = true)
                protected String name2;

                /**
                 * Ruft den Wert der gueltigVon-Eigenschaft ab.
                 * 
                 * @return
                 *     possible object is
                 *     {@link String }
                 *     
                 */
                public String getGueltigVon() {
                    return gueltigVon;
                }

                /**
                 * Legt den Wert der gueltigVon-Eigenschaft fest.
                 * 
                 * @param value
                 *     allowed object is
                 *     {@link String }
                 *     
                 */
                public void setGueltigVon(String value) {
                    this.gueltigVon = value;
                }

                /**
                 * Ruft den Wert der name1-Eigenschaft ab.
                 * 
                 * @return
                 *     possible object is
                 *     {@link String }
                 *     
                 */
                public String getName1() {
                    return name1;
                }

                /**
                 * Legt den Wert der name1-Eigenschaft fest.
                 * 
                 * @param value
                 *     allowed object is
                 *     {@link String }
                 *     
                 */
                public void setName1(String value) {
                    this.name1 = value;
                }

                /**
                 * Ruft den Wert der name2-Eigenschaft ab.
                 * 
                 * @return
                 *     possible object is
                 *     {@link String }
                 *     
                 */
                public String getName2() {
                    return name2;
                }

                /**
                 * Legt den Wert der name2-Eigenschaft fest.
                 * 
                 * @param value
                 *     allowed object is
                 *     {@link String }
                 *     
                 */
                public void setName2(String value) {
                    this.name2 = value;
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
             *         &lt;element name="TeleKommunikation" maxOccurs="unbounded"&gt;
             *           &lt;complexType&gt;
             *             &lt;complexContent&gt;
             *               &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType"&gt;
             *                 &lt;sequence&gt;
             *                   &lt;element name="qualifier" type="{http://www.w3.org/2001/XMLSchema}string"/&gt;
             *                   &lt;element name="art" type="{http://www.w3.org/2001/XMLSchema}string"/&gt;
             *                   &lt;element name="vorwahl" type="{http://www.w3.org/2001/XMLSchema}string" minOccurs="0"/&gt;
             *                   &lt;element name="rufnummer" type="{http://www.w3.org/2001/XMLSchema}string" minOccurs="0"/&gt;
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
                "teleKommunikation"
            })
            public static class TeleKommunikationen {

                @XmlElement(name = "TeleKommunikation", required = true)
                protected List<EGeckoPersonenkonten.Personenkonto.Geschaeftspartner.TeleKommunikationen.TeleKommunikation> teleKommunikation;

                /**
                 * Gets the value of the teleKommunikation property.
                 * 
                 * <p>
                 * This accessor method returns a reference to the live list,
                 * not a snapshot. Therefore any modification you make to the
                 * returned list will be present inside the JAXB object.
                 * This is why there is not a <CODE>set</CODE> method for the teleKommunikation property.
                 * 
                 * <p>
                 * For example, to add a new item, do as follows:
                 * <pre>
                 *    getTeleKommunikation().add(newItem);
                 * </pre>
                 * 
                 * 
                 * <p>
                 * Objects of the following type(s) are allowed in the list
                 * {@link EGeckoPersonenkonten.Personenkonto.Geschaeftspartner.TeleKommunikationen.TeleKommunikation }
                 * 
                 * 
                 */
                public List<EGeckoPersonenkonten.Personenkonto.Geschaeftspartner.TeleKommunikationen.TeleKommunikation> getTeleKommunikation() {
                    if (teleKommunikation == null) {
                        teleKommunikation = new ArrayList<EGeckoPersonenkonten.Personenkonto.Geschaeftspartner.TeleKommunikationen.TeleKommunikation>();
                    }
                    return this.teleKommunikation;
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
                 *         &lt;element name="qualifier" type="{http://www.w3.org/2001/XMLSchema}string"/&gt;
                 *         &lt;element name="art" type="{http://www.w3.org/2001/XMLSchema}string"/&gt;
                 *         &lt;element name="vorwahl" type="{http://www.w3.org/2001/XMLSchema}string" minOccurs="0"/&gt;
                 *         &lt;element name="rufnummer" type="{http://www.w3.org/2001/XMLSchema}string" minOccurs="0"/&gt;
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
                    "qualifier",
                    "art",
                    "vorwahl",
                    "rufnummer"
                })
                public static class TeleKommunikation {

                    @XmlElement(required = true)
                    protected String qualifier;
                    @XmlElement(required = true)
                    protected String art;
                    protected String vorwahl;
                    protected String rufnummer;

                    /**
                     * Ruft den Wert der qualifier-Eigenschaft ab.
                     * 
                     * @return
                     *     possible object is
                     *     {@link String }
                     *     
                     */
                    public String getQualifier() {
                        return qualifier;
                    }

                    /**
                     * Legt den Wert der qualifier-Eigenschaft fest.
                     * 
                     * @param value
                     *     allowed object is
                     *     {@link String }
                     *     
                     */
                    public void setQualifier(String value) {
                        this.qualifier = value;
                    }

                    /**
                     * Ruft den Wert der art-Eigenschaft ab.
                     * 
                     * @return
                     *     possible object is
                     *     {@link String }
                     *     
                     */
                    public String getArt() {
                        return art;
                    }

                    /**
                     * Legt den Wert der art-Eigenschaft fest.
                     * 
                     * @param value
                     *     allowed object is
                     *     {@link String }
                     *     
                     */
                    public void setArt(String value) {
                        this.art = value;
                    }

                    /**
                     * Ruft den Wert der vorwahl-Eigenschaft ab.
                     * 
                     * @return
                     *     possible object is
                     *     {@link String }
                     *     
                     */
                    public String getVorwahl() {
                        return vorwahl;
                    }

                    /**
                     * Legt den Wert der vorwahl-Eigenschaft fest.
                     * 
                     * @param value
                     *     allowed object is
                     *     {@link String }
                     *     
                     */
                    public void setVorwahl(String value) {
                        this.vorwahl = value;
                    }

                    /**
                     * Ruft den Wert der rufnummer-Eigenschaft ab.
                     * 
                     * @return
                     *     possible object is
                     *     {@link String }
                     *     
                     */
                    public String getRufnummer() {
                        return rufnummer;
                    }

                    /**
                     * Legt den Wert der rufnummer-Eigenschaft fest.
                     * 
                     * @param value
                     *     allowed object is
                     *     {@link String }
                     *     
                     */
                    public void setRufnummer(String value) {
                        this.rufnummer = value;
                    }

                }

            }

        }

    }

}

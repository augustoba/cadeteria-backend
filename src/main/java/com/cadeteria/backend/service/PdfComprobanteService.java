package com.cadeteria.backend.service;

import com.cadeteria.backend.model.Pedido;
import org.openpdf.text.Document;
import org.openpdf.text.Element;
import org.openpdf.text.Font;
import org.openpdf.text.FontFactory;
import org.openpdf.text.Image;
import org.openpdf.text.Paragraph;
import org.openpdf.text.Phrase;
import org.openpdf.text.Rectangle;
import org.openpdf.text.pdf.PdfPCell;
import org.openpdf.text.pdf.PdfPTable;
import org.openpdf.text.pdf.PdfWriter;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * Comprobante descargable de la pagina publica de seguimiento (spec 5.7/6, diseno sección 8).
 * Formato de cupón único (antes se manejaban 3 talones en papel: recepción, CADEM e interesado;
 * acá se consolida todo en un solo comprobante con los mismos datos).
 */
@Service
public class PdfComprobanteService {

    private static final DateTimeFormatter FORMATO_FECHA =
            DateTimeFormatter.ofPattern("dd/MM/yyyy").withZone(ZoneId.of("America/Argentina/Buenos_Aires"));
    private static final DateTimeFormatter FORMATO_HORA =
            DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.of("America/Argentina/Buenos_Aires"));

    private static final Color NARANJA = new Color(0xFC, 0x69, 0x00);
    private static final Color OSCURO = new Color(0x1E, 0x1E, 0x1E);


    public byte[] generar(Pedido p) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Document doc = new Document();
            doc.setMargins(48, 48, 40, 40);
            PdfWriter.getInstance(doc, out);
            doc.open();

            Font tituloFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 15, OSCURO);
            Font cuponFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12, NARANJA);
            Font labelFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, OSCURO);
            Font valueFont = FontFactory.getFont(FontFactory.HELVETICA, 10, OSCURO);
            Font footerFont = FontFactory.getFont(FontFactory.HELVETICA_OBLIQUE, 8, Color.GRAY);

            PdfPTable tarjeta = new PdfPTable(1);
            tarjeta.setWidthPercentage(100);

            PdfPCell contenedor = new PdfPCell();
            contenedor.setPadding(18);
            contenedor.setBorderColor(NARANJA);
            contenedor.setBorderWidth(1.5f);

            // Encabezado: logo | nombre de la cadetería | comprobante y cupón (2026-09-25: sin el
            // teléfono fijo, que ya no se usa)
            PdfPTable encabezado = new PdfPTable(3);
            encabezado.setWidthPercentage(100);
            encabezado.setWidths(new float[]{1f, 2.2f, 2f});

            PdfPCell celdaLogo = new PdfPCell();
            celdaLogo.setBorder(Rectangle.NO_BORDER);
            celdaLogo.setVerticalAlignment(Element.ALIGN_MIDDLE);
            try {
                Image logo = Image.getInstance(getClass().getResource("/branding/logo-cadem.png"));
                logo.scaleToFit(64, 64);
                celdaLogo.addElement(logo);
            } catch (Exception ignored) {
                celdaLogo.addElement(new Paragraph("CADEM", cuponFont));
            }
            encabezado.addCell(celdaLogo);

            PdfPCell celdaNombre = new PdfPCell();
            celdaNombre.setBorder(Rectangle.NO_BORDER);
            celdaNombre.setVerticalAlignment(Element.ALIGN_MIDDLE);
            Paragraph nombrePar = new Paragraph();
            nombrePar.add(new Phrase("CADEM ", FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18, NARANJA)));
            nombrePar.add(new Phrase("CADETERÍA", FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18, OSCURO)));
            celdaNombre.addElement(nombrePar);
            encabezado.addCell(celdaNombre);

            PdfPCell celdaCupon = new PdfPCell();
            celdaCupon.setBorder(Rectangle.NO_BORDER);
            celdaCupon.setVerticalAlignment(Element.ALIGN_MIDDLE);
            celdaCupon.setHorizontalAlignment(Element.ALIGN_RIGHT);
            Paragraph tituloPar = new Paragraph("Comprobante de envío", tituloFont);
            tituloPar.setAlignment(Element.ALIGN_RIGHT);
            Paragraph cuponPar = new Paragraph("Cupón Nº " + p.getNumero(), cuponFont);
            cuponPar.setAlignment(Element.ALIGN_RIGHT);
            celdaCupon.addElement(tituloPar);
            celdaCupon.addElement(cuponPar);
            encabezado.addCell(celdaCupon);

            contenedor.addElement(encabezado);
            contenedor.addElement(lineaDivisoria());

            PdfPTable datos = new PdfPTable(2);
            datos.setWidthPercentage(100);
            datos.setWidths(new float[]{1f, 2f});
            datos.setSpacingBefore(8);

            agregarFila(datos, "Fecha:", FORMATO_FECHA.format(p.getCreadoEn()), labelFont, valueFont);
            agregarFila(datos, "Hora:", FORMATO_HORA.format(p.getCreadoEn()), labelFont, valueFont);
            agregarFila(datos, "Cliente:", p.getClienteNombre(), labelFont, valueFont);
            agregarFila(datos, "Solicita:", p.getClienteTelefono(), labelFont, valueFont);
            agregarFila(datos, "Origen:", p.getOrigenDireccion(), labelFont, valueFont);
            agregarFila(datos, "Destino:", p.getDestinoDireccion(), labelFont, valueFont);
            agregarFila(datos, "Efectivo:", formatoMoneda(p.getMontoDeclarado()), labelFont, valueFont);
            if (p.isLlevaValores()) {
                agregarFila(datos, "Objetos de valor:",
                        p.getMontoValores() != null ? formatoMoneda(p.getMontoValores()) : "Sí (sin monto declarado)", labelFont, valueFont);
            }
            agregarFila(datos, "Valor trámite:", formatoMoneda(p.getPrecio()), labelFont, valueFont);
            if (p.getCadeteAsignado() != null) {
                String movil = p.getCadeteAsignado().getNombre() + " " + p.getCadeteAsignado().getApellido();
                if (p.getCadeteAsignado().getDni() != null && !p.getCadeteAsignado().getDni().isBlank()) {
                    movil += " - DNI " + p.getCadeteAsignado().getDni();
                }
                if (p.getCadeteAsignado().getTelefono() != null && !p.getCadeteAsignado().getTelefono().isBlank()) {
                    movil += " - Tel. " + p.getCadeteAsignado().getTelefono();
                }
                agregarFila(datos, "Móvil:", movil, labelFont, valueFont);
            }
            if (p.getEntregaReceptorNombre() != null && !p.getEntregaReceptorNombre().isBlank()) {
                agregarFila(datos, "Recibió:", p.getEntregaReceptorNombre(), labelFont, valueFont);
            }
            if (p.getFinalizadoEn() == null) {
                agregarFila(datos, "Estado:", p.getRetiradoEn() != null ? "Retirado, en camino al destino" : "En camino a retirar",
                        labelFont, valueFont);
            }
            if (p.getFinalizadoEn() != null) {
                agregarFila(datos, "Entregado:",
                        FORMATO_FECHA.format(p.getFinalizadoEn()) + " " + FORMATO_HORA.format(p.getFinalizadoEn()),
                        labelFont, valueFont);
            }

            contenedor.addElement(datos);
            contenedor.addElement(lineaDivisoria());

            // Sin "Firma de conformidad" (2026-09-25): la firma de quien recibe la toma el cadete en la app.
            Paragraph piePar = new Paragraph("Gracias por confiar en CADEM cadetería.", footerFont);
            piePar.setSpacingBefore(16);
            contenedor.addElement(piePar);

            tarjeta.addCell(contenedor);
            doc.add(tarjeta);

            doc.close();
            return out.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo generar el comprobante PDF", e);
        }
    }

    private void agregarFila(PdfPTable tabla, String label, String value, Font labelFont, Font valueFont) {
        PdfPCell celdaLabel = new PdfPCell(new Phrase(label, labelFont));
        celdaLabel.setBorder(Rectangle.NO_BORDER);
        celdaLabel.setPaddingBottom(4);
        tabla.addCell(celdaLabel);

        PdfPCell celdaValue = new PdfPCell(new Phrase(value == null ? "" : value, valueFont));
        celdaValue.setBorder(Rectangle.NO_BORDER);
        celdaValue.setPaddingBottom(4);
        tabla.addCell(celdaValue);
    }

    private PdfPTable lineaDivisoria() {
        PdfPTable linea = new PdfPTable(1);
        linea.setWidthPercentage(100);
        linea.setSpacingBefore(6);
        linea.setSpacingAfter(6);
        PdfPCell celda = new PdfPCell();
        celda.setFixedHeight(2f);
        celda.setBackgroundColor(NARANJA);
        celda.setBorder(Rectangle.NO_BORDER);
        linea.addCell(celda);
        return linea;
    }

    private String formatoMoneda(BigDecimal valor) {
        BigDecimal v = valor == null ? BigDecimal.ZERO : valor;
        return "$ " + v.setScale(2, java.math.RoundingMode.HALF_UP);
    }
}

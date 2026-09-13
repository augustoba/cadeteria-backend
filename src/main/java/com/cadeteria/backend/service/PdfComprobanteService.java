package com.cadeteria.backend.service;

import com.cadeteria.backend.model.Pedido;
import org.openpdf.text.Document;
import org.openpdf.text.Font;
import org.openpdf.text.FontFactory;
import org.openpdf.text.Paragraph;
import org.openpdf.text.pdf.PdfWriter;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/** Comprobante descargable de la pagina publica de seguimiento (spec 5.7/6, diseno sección 8). */
@Service
public class PdfComprobanteService {

    private static final DateTimeFormatter FORMATO_FECHA =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").withZone(ZoneId.of("America/Argentina/Buenos_Aires"));

    public byte[] generar(Pedido p) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Document doc = new Document();
            PdfWriter.getInstance(doc, out);
            doc.open();

            Font titulo = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18);
            Font normal = FontFactory.getFont(FontFactory.HELVETICA, 12);

            doc.add(new Paragraph("Comprobante de entrega", titulo));
            doc.add(new Paragraph(" "));
            doc.add(new Paragraph("Pedido Nº " + p.getNumero(), normal));
            doc.add(new Paragraph("Cliente: " + p.getClienteNombre() + " (" + p.getClienteTelefono() + ")", normal));
            doc.add(new Paragraph("Origen: " + p.getOrigenDireccion(), normal));
            doc.add(new Paragraph("Destino: " + p.getDestinoDireccion(), normal));
            doc.add(new Paragraph("Precio: $" + p.getPrecio(), normal));
            if (p.getCadeteAsignado() != null) {
                doc.add(new Paragraph("Cadete: " + p.getCadeteAsignado().getNombre()
                        + " " + p.getCadeteAsignado().getApellido(), normal));
            }
            if (p.getEntregaReceptorNombre() != null) {
                doc.add(new Paragraph("Recibio: " + p.getEntregaReceptorNombre(), normal));
            }
            if (p.getFinalizadoEn() != null) {
                doc.add(new Paragraph("Entregado: " + FORMATO_FECHA.format(p.getFinalizadoEn()), normal));
            }

            doc.close();
            return out.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo generar el comprobante PDF", e);
        }
    }
}

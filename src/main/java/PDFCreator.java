import com.lowagie.text.DocumentException;
import com.lowagie.text.Image;
import com.lowagie.text.PageSize;
import com.lowagie.text.pdf.PdfWriter;
import org.apache.batik.transcoder.TranscoderInput;
import org.apache.batik.transcoder.TranscoderOutput;
import org.apache.batik.transcoder.image.PNGTranscoder;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonReader;
import javax.swing.*;
import java.awt.*;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Scanner;

public class PDFCreator {
    String outputName = "";
    String inputURL;
    String jsonValueToSearch;
    String URLsubstringToFind;
    int URLlengthSubstringToSequence;
    String preferedMode;
    JTextArea jTextArea = null;

    public PDFCreator(String outputName, String inputURL, String jsonValueToSearch, String URLsubstringToFind, int URLlengthSubstringToSequence, String preferedMode) {
        this.outputName = outputName;
        this.inputURL = inputURL;
        this.jsonValueToSearch = jsonValueToSearch;
        this.URLsubstringToFind = URLsubstringToFind;
        this.URLlengthSubstringToSequence = URLlengthSubstringToSequence;
        this.preferedMode = preferedMode;
    }

    public void sout(String output) {
        if (jTextArea != null) {
            jTextArea.append(output + "\n");
        } else {
            System.out.println(output);
        }
    }

    public PDFCreator(String outputName, String inputURL, String jsonValueToSearch, String URLsubstringToFind, int URLlengthSubstringToSequence) {
        this.outputName = outputName;
        this.inputURL = inputURL;
        this.jsonValueToSearch = jsonValueToSearch;
        this.URLsubstringToFind = URLsubstringToFind;
        this.URLlengthSubstringToSequence = URLlengthSubstringToSequence;
        this.preferedMode = "svg";
    }

    public void createPDFFromImages() throws Exception {
        JPanel jframe = new JPanel(new GridLayout(0, 1));
        jframe.setPreferredSize(new Dimension(640, 480));
        jframe.setLayout(new BorderLayout());

        jTextArea = new JTextArea();
        jTextArea.append("Debug log. Wait for all files to process. After getting \"ALL DONE \", press OK to close window.");
        JScrollPane scroll = new JScrollPane(jTextArea); //place the JTextArea in a scroll pane
        jframe.add(scroll, BorderLayout.CENTER);
        Thread t = new Thread(() -> {
            int result = JOptionPane.showConfirmDialog(null, jframe, "Extractor",
                    JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        });
        t.start();
        System.out.println("FRAME SHOWN?");
        //find URLs
        String url = jsonValueExtractor(inputURL, jsonValueToSearch);
        url = url.replace("\\/", "/");
        /*String mode = url.contains(".png") ? "png" : "svg";
        if (!mode.equals(preferedMode)) {
            if (!isUrlStatus404(url.replace("." + mode, "." + preferedMode))) {
                url = url.replace("." + mode, "." + preferedMode);
                mode = preferedMode;

            }
        }*/
        //always use svg mode for now, and check for existence of .png of .svg does not exist
        String mode = "svg";

        //download images
        url = url.replace(".png", ".svg"); //Always start with .svg
        ArrayList<String> downloadedFiles = new ArrayList<>();
        int counter = 0;
        String downloadUrl = replaceUrlWithcounterSequence(url, counter);
        //check prefered mode url


        sout("used mode: " + mode);
        boolean flagLastFile = false;
        while (!flagLastFile) {
            String urlToDownload = "";
            if (!isUrlStatus404(downloadUrl)) {
                urlToDownload = downloadUrl;
            } else if (!isUrlStatus404(downloadUrl.replace(".svg", ".png"))) {
                urlToDownload = downloadUrl.replace(".svg", ".png");
            } else {
                //no valid .png or .svg url found
                //check if next url does work. If it does, then skip this one (for now).
                String nextUrlToCheck = replaceUrlWithcounterSequence(url, counter + 1);
                if (!isUrlStatus404(nextUrlToCheck) || !isUrlStatus404(nextUrlToCheck.replace(".svg", ".png"))) {
                    //next file still okay, increase counter
                    sout("!ERROR: page number " + counter + " was not able to load and has been skipped.");
                    urlToDownload = ""; //safeguard
                    flagLastFile = false;
                } else {
                    //no more files
                    flagLastFile = true;
                }
            }

            if (!urlToDownload.equals("")) {
                downloadedFiles.add(downloadFile(new URL(urlToDownload), "_" + outputName + counter
                        + "." + (urlToDownload.contains(".png") ? "png" : "svg")));
            }
            counter++;
            downloadUrl = replaceUrlWithcounterSequence(url, counter);
            sout(downloadUrl);
        }
        ArrayList<String> pngFiles = new ArrayList<>();
            //convert all files first
            for (String fileName : downloadedFiles) {
                if (fileName.contains(".svg")){
                    //convert to .png
                    pngFiles.add(createImage(fileName));
                    deleteFile(fileName);
                }else{
                    pngFiles.add(fileName);
                }
            }


        try {
            Scanner sc = new Scanner(System.in);
            // Destination = D:/Destination/;
            String destination = "./";
            String name = outputName;
            String sourcePath = "";
            for (String imageFile : pngFiles) {
                sourcePath += "," + imageFile;
            }
            sourcePath = sourcePath.substring(1);
            // Source = D:/Source/a.jpg,D:/Source/b.jpg;
            imagesToPdf(destination, name, sourcePath);
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        sout("PDF created with name " + outputName + ".pdf");
        sout("deleting images that were extracted");
        for (String pngFile : pngFiles) {
            //deleteFile(pngFile);
        }
        sout("All done!");
    }

    private String replaceUrlWithcounterSequence(String url, int counter) {
        return url.substring(0, url.indexOf(URLsubstringToFind) + URLsubstringToFind.length())
                + counter + url.substring(url.indexOf(URLsubstringToFind) + URLsubstringToFind.length() + URLlengthSubstringToSequence);
    }

    private void deleteFile(String fileName) {
        File fileToDelete = new File(fileName);
        if (fileToDelete.delete()) {
            sout(fileName + " File deleted");
        } else {
            sout(fileName + " deletion Operation failed");
        }
    }

    private String downloadFile(URL url, String outputFileName) throws IOException {
        try (InputStream in = url.openStream();
             ReadableByteChannel rbc = Channels.newChannel(in);
             FileOutputStream fos = new FileOutputStream(outputFileName)) {
            fos.getChannel().transferFrom(rbc, 0, Long.MAX_VALUE);
        }
        return outputFileName;
    }

    private boolean isUrlStatus404(String url) throws IOException {
        URL u = new URL(url);
        HttpURLConnection huc = (HttpURLConnection) u.openConnection();
        huc.setRequestMethod("GET");  //OR  huc.setRequestMethod ("HEAD");
        huc.connect();
        int code = huc.getResponseCode();
        sout("is " + (code == 404 ? "404" : "valid") + "with url " + url);
        return code == 404;
    }


    private String jsonValueExtractor(String URL, String jsonValue) throws IOException {
        Document doc = Jsoup.connect(URL).get();
        Elements scripts = doc.select("script");
        for (Element script : scripts) {
            //json script with the needed value, todo: check if valid json
            if (script.html().contains("\"" + jsonValue + "\"")) {
                String extraction = script.html();
                sout(extraction);
                //adhoc json extractor to avoid json library
                String key = "\"" + jsonValue + "\"" + ": \"";
                int beginIndex = extraction.indexOf(key) + key.length();
                int endIndex = extraction.indexOf("\"", beginIndex + 1);
                String content = extraction.substring(beginIndex, endIndex);
                sout(content);
                sout("________________________");
                return content;
            }
        }
        return "";
    }

    private String createImage(String fileNameSVG) throws Exception {
        String pngFileName = fileNameSVG.replace(".svg", ".png");
        String svg_URI_input = new File(fileNameSVG).toURL().toString();
        TranscoderInput input_svg_image = new TranscoderInput(svg_URI_input);
        //Step-2: Define OutputStream to PNG Image and attach to TranscoderOutput
        OutputStream png_ostream = new FileOutputStream(pngFileName);
        TranscoderOutput output_png_image = new TranscoderOutput(png_ostream);
        // Step-3: Create PNGTranscoder and define hints if required
        PNGTranscoder my_converter = new PNGTranscoder();
        my_converter.addTranscodingHint(PNGTranscoder.KEY_PIXEL_UNIT_TO_MILLIMETER, 1.0f);
        my_converter.addTranscodingHint(PNGTranscoder.KEY_WIDTH, 2480f);
        my_converter.addTranscodingHint(PNGTranscoder.KEY_HEIGHT,3508f);
        // Step-4: Convert and Write output
        my_converter.transcode(input_svg_image, output_png_image);
        png_ostream.flush();
        png_ostream.close();
        return pngFileName;
    }

    private void imagesToPdf(String destination, String pdfName, String imagFileSource) throws IOException, DocumentException {

        com.lowagie.text.Document document = new com.lowagie.text.Document(PageSize.A4, 20.0f, 20.0f, 20.0f, 150.0f);
        String desPath = destination;

        File destinationDirectory = new File(desPath);
        if (!destinationDirectory.exists()) {
            destinationDirectory.mkdir();
            sout("DESTINATION FOLDER CREATED -> " + destinationDirectory.getAbsolutePath());
        } else if (destinationDirectory.exists()) {
            sout("DESTINATION FOLDER ALREADY CREATED!!!");
        } else {
            sout("DESTINATION FOLDER NOT CREATED!!!");
        }

        File file = new File(destinationDirectory, pdfName + ".pdf");

        FileOutputStream fileOutputStream = new FileOutputStream(file);

        PdfWriter pdfWriter = PdfWriter.getInstance(document, fileOutputStream);
        document.open();

        sout("CONVERTER START.....");

        String[] splitImagFiles = imagFileSource.split(",");

        for (String singleImage : splitImagFiles) {
            Image image = Image.getInstance(singleImage);
            document.setPageSize(image);
            document.newPage();
            image.setAbsolutePosition(0, 0);
            document.add(image);
        }

        document.close();
        sout("CONVERTER STOPTED.....");


    }

    public static JsonObject jsonFromString(String fileName) throws FileNotFoundException {
        InputStream fis = new FileInputStream(fileName);
        JsonReader jsonReader = Json.createReader(fis);
        JsonObject object = jsonReader.readObject();
        jsonReader.close();

        return object;
    }

    public static String readFile(String path, Charset encoding)
            throws IOException {
        byte[] encoded = Files.readAllBytes(Paths.get(path));
        return new String(encoded, encoding);
    }

    public static void display(JsonObject config) throws Exception {
        JTextField field1 = new JTextField("");
        JTextField field2 = new JTextField("");
        JPanel panel = new JPanel(new GridLayout(0, 1));
        panel.add(new JLabel("URL to extract: "));
        panel.add(field1);
        panel.add(new JLabel("pdf filename: "));
        panel.add(field2);
        int result = JOptionPane.showConfirmDialog(null, panel, "Extractor",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (result == JOptionPane.OK_OPTION) {
            PDFCreator pdfCreator = new PDFCreator(field2.getText(), field1.getText(), config.getString("ValueToSearch"), config.getString("SubstringUrl"), config.getInt("sequencerLength"));
            pdfCreator.createPDFFromImages();
        } else {
            System.out.println("Cancelled");
        }
    }

    public static void main(String[] args) throws Exception {
        JsonObject config = jsonFromString("src/main/resources/config.json");
        PDFCreator.display(config);
    }
}

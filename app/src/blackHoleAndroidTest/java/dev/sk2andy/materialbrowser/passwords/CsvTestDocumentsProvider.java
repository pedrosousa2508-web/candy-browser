package dev.sk2andy.materialbrowser.passwords;

import android.database.Cursor;
import android.database.MatrixCursor;
import android.os.CancellationSignal;
import android.os.ParcelFileDescriptor;
import android.provider.DocumentsContract.Document;
import android.provider.DocumentsContract.Root;
import android.provider.DocumentsProvider;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;

/** Fake SAF documents only. Runs in the test APK process, without the app's shared Kotlin runtime. */
public final class CsvTestDocumentsProvider extends DocumentsProvider {
    public static final String AUTHORITY = "dev.sk2andy.materialbrowser.blackholedev.test.csv.documents";
    public static final String ROOT_TITLE = "Black Hole CSV Tests";
    public static final String INVENTED_CSV = "name,url,username,password\r\n"
        + "Fake site,https://example.org,fake-user,\" fake,\"\"A\"\"  \"\r\n"
        + "Fake duplicate,https://example.org,fake-user,\" fake,\"\"A\"\"  \"\r\n"
        + "Fake version,https://example.org,fake-user,fake-password-B\r\n"
        + "Fake app,android://fake@com.example.app,fake-app-user,fake-app-password\r\n"
        + "Invalid,javascript:fake,fake-user,fake-invalid\r\n";
    private static final String[] ROOT_COLUMNS = {Root.COLUMN_ROOT_ID, Root.COLUMN_DOCUMENT_ID,
        Root.COLUMN_TITLE, Root.COLUMN_FLAGS, Root.COLUMN_MIME_TYPES, Root.COLUMN_AVAILABLE_BYTES};
    private static final String[] DOCUMENT_COLUMNS = {Document.COLUMN_DOCUMENT_ID,
        Document.COLUMN_DISPLAY_NAME, Document.COLUMN_MIME_TYPE, Document.COLUMN_FLAGS,
        Document.COLUMN_SIZE, Document.COLUMN_LAST_MODIFIED};
    private File directory;

    @Override public boolean onCreate() {
        directory = new File(getContext().getNoBackupFilesDir(), "invented-csv-documents");
        if (!directory.mkdirs() && !directory.isDirectory()) return false;
        try (FileOutputStream output = new FileOutputStream(file("input"))) {
            output.write(INVENTED_CSV.getBytes(StandardCharsets.UTF_8));
        } catch (IOException ignored) {
            return false;
        }
        return true;
    }

    @Override public Cursor queryRoots(String[] projection) {
        MatrixCursor cursor = new MatrixCursor(projection == null ? ROOT_COLUMNS : projection);
        Map<String, Object> values = new HashMap<>();
        values.put(Root.COLUMN_ROOT_ID, "csv-tests");
        values.put(Root.COLUMN_DOCUMENT_ID, "root");
        values.put(Root.COLUMN_TITLE, ROOT_TITLE);
        values.put(Root.COLUMN_FLAGS, Root.FLAG_SUPPORTS_CREATE | Root.FLAG_LOCAL_ONLY | Root.FLAG_SUPPORTS_IS_CHILD);
        values.put(Root.COLUMN_MIME_TYPES, "text/csv");
        values.put(Root.COLUMN_AVAILABLE_BYTES, directory.getUsableSpace());
        addRow(cursor, values);
        return cursor;
    }

    @Override public Cursor queryDocument(String documentId, String[] projection) {
        MatrixCursor cursor = new MatrixCursor(projection == null ? DOCUMENT_COLUMNS : projection);
        addDocument(cursor, documentId);
        return cursor;
    }

    @Override public Cursor queryChildDocuments(String parentDocumentId, String[] projection, String sortOrder) {
        if (!"root".equals(parentDocumentId)) throw new IllegalArgumentException("Invalid test parent");
        MatrixCursor cursor = new MatrixCursor(projection == null ? DOCUMENT_COLUMNS : projection);
        File[] files = directory.listFiles();
        if (files != null) {
            Arrays.sort(files, Comparator.comparing(File::getName));
            for (File child : files) addDocument(cursor, child.getName());
        }
        return cursor;
    }

    @Override public boolean isChildDocument(String parentDocumentId, String documentId) {
        return "root".equals(parentDocumentId) && !"root".equals(documentId) && file(documentId).isFile();
    }

    @Override public String createDocument(String parentDocumentId, String mimeType, String displayName)
            throws FileNotFoundException {
        if (!"root".equals(parentDocumentId) || !"text/csv".equals(mimeType)) {
            throw new FileNotFoundException("Invalid test destination");
        }
        int number = 1;
        while (file("export-" + number).exists()) number++;
        String id = "export-" + number;
        try {
            if (!file(id).createNewFile()) throw new IOException();
        } catch (IOException ignored) {
            throw new FileNotFoundException("Unable to create invented fixture");
        }
        return id;
    }

    @Override public void deleteDocument(String documentId) throws FileNotFoundException {
        if (!documentId.startsWith("export-") || !file(documentId).delete()) {
            throw new FileNotFoundException("Unable to delete invented fixture");
        }
    }

    @Override public ParcelFileDescriptor openDocument(String documentId, String mode, CancellationSignal signal)
            throws FileNotFoundException {
        if ("input".equals(documentId) && !"r".equals(mode)) {
            throw new FileNotFoundException("Read-only invented fixture");
        }
        return ParcelFileDescriptor.open(file(documentId), ParcelFileDescriptor.parseMode(mode));
    }

    private File file(String id) {
        if (!"input".equals(id) && !id.matches("export-[0-9]+")) {
            throw new IllegalArgumentException("Invalid test document ID");
        }
        return new File(directory, id);
    }

    private void addDocument(MatrixCursor cursor, String id) {
        boolean root = "root".equals(id);
        File target = root ? directory : file(id);
        Map<String, Object> values = new HashMap<>();
        values.put(Document.COLUMN_DOCUMENT_ID, id);
        values.put(Document.COLUMN_DISPLAY_NAME, root ? ROOT_TITLE :
            ("input".equals(id) ? "invented-input.csv" : "invented-" + id + ".csv"));
        values.put(Document.COLUMN_MIME_TYPE, root ? Document.MIME_TYPE_DIR : "text/csv");
        values.put(Document.COLUMN_FLAGS, root ? Document.FLAG_DIR_SUPPORTS_CREATE :
            ("input".equals(id) ? 0 : Document.FLAG_SUPPORTS_WRITE | Document.FLAG_SUPPORTS_DELETE));
        values.put(Document.COLUMN_SIZE, root ? 0L : target.length());
        values.put(Document.COLUMN_LAST_MODIFIED, target.lastModified());
        addRow(cursor, values);
    }

    private static void addRow(MatrixCursor cursor, Map<String, Object> values) {
        String[] columns = cursor.getColumnNames();
        Object[] row = new Object[columns.length];
        for (int index = 0; index < columns.length; index++) row[index] = values.get(columns[index]);
        cursor.addRow(row);
    }
}

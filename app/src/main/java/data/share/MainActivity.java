package data.share;
import android.app.Activity;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.os.Bundle;
import android.os.CancellationSignal;  // 添加这个导入
import android.os.ParcelFileDescriptor;
import android.provider.DocumentsContract;
import android.provider.DocumentsProvider;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.MimeTypeMap;  // 添加这个导入
import android.widget.BaseAdapter;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
public class MainActivity extends Activity {
    private static final String SHARED_UID = "cm.sn";
    private List<AppInfo> appList = new ArrayList<>();
    private AppAdapter adapter;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(50, 50, 50, 50);
        setContentView(root);
        TextView title = new TextView(this);
        title.setText("共享UID：android:sharedUserId=\"cm.sn\"");
        title.setTextSize(18);
        root.addView(title);
        ListView listView = new ListView(this);
        listView.setLayoutParams(new LinearLayout.LayoutParams(
									 LinearLayout.LayoutParams.MATCH_PARENT, 
									 LinearLayout.LayoutParams.MATCH_PARENT));
        root.addView(listView);
        adapter = new AppAdapter();
        listView.setAdapter(adapter);
        scanSharedUidApps();
    }
    private void scanSharedUidApps() {
        appList.clear();
        PackageManager pm = getPackageManager();
        List<PackageInfo> packages = pm.getInstalledPackages(0);
        for (PackageInfo pkg : packages) {
            if (pkg != null && SHARED_UID.equals(pkg.sharedUserId) && pkg.applicationInfo != null) {
                String appName = pkg.applicationInfo.loadLabel(pm).toString();
                appList.add(new AppInfo(appName, pkg.packageName, pkg.applicationInfo.dataDir));
            }
        }
        adapter.notifyDataSetChanged();
    }
    private class AppAdapter extends BaseAdapter {
        @Override
        public int getCount() { return appList.size(); }
        @Override
        public Object getItem(int i) { return appList.get(i); }
        @Override
        public long getItemId(int i) { return i; }
        @Override
        public View getView(int i, View view, ViewGroup viewGroup) {
            TextView tv = new TextView(MainActivity.this);
            tv.setPadding(0, 20, 0, 20);
            tv.setTextSize(14);
            AppInfo info = appList.get(i);
            tv.setText(info.name + "\n" + info.packageName + "\n" + info.dataDir);
            return tv;
        }
    }
    private static class AppInfo {
        String name, packageName, dataDir;
        AppInfo(String name, String packageName, String dataDir) {
            this.name = name;
            this.packageName = packageName;
            this.dataDir = dataDir;
        }
    }
    public static class UidDataProvider extends DocumentsProvider {
        private static final String SHARED_UID = "cm.sn";
        private static final String ROOT_ID_PREFIX = "pkg_";
        @Override
        public boolean onCreate() {
            return true;
        }
        @Override
        public Cursor queryRoots(String[] projection) {
            MatrixCursor result = new MatrixCursor(projection != null ? projection : new String[]{
													   DocumentsContract.Root.COLUMN_ROOT_ID,
													   DocumentsContract.Root.COLUMN_DOCUMENT_ID,
													   DocumentsContract.Root.COLUMN_TITLE,
													   DocumentsContract.Root.COLUMN_SUMMARY,
													   DocumentsContract.Root.COLUMN_FLAGS,
													   DocumentsContract.Root.COLUMN_ICON
												   });
            if (getContext() == null) return result;
            PackageManager pm = getContext().getPackageManager();
            for (PackageInfo pkg : pm.getInstalledPackages(0)) {
                if (pkg != null && SHARED_UID.equals(pkg.sharedUserId) && pkg.applicationInfo != null) {
                    String pkgName = pkg.packageName;
                    String rootId = ROOT_ID_PREFIX + pkgName;
                    result.newRow().add(DocumentsContract.Root.COLUMN_ROOT_ID, rootId)
                        .add(DocumentsContract.Root.COLUMN_DOCUMENT_ID, rootId)
                        .add(DocumentsContract.Root.COLUMN_TITLE, pkg.applicationInfo.loadLabel(pm).toString())
                        .add(DocumentsContract.Root.COLUMN_SUMMARY, pkgName)
                        .add(DocumentsContract.Root.COLUMN_FLAGS, DocumentsContract.Root.FLAG_SUPPORTS_CREATE)
                        .add(DocumentsContract.Root.COLUMN_ICON, pkg.applicationInfo.icon);
                }
            }
            return result;
        }
        private File getFileFromDocId(String docId) {
            if (docId == null || getContext() == null) return null;
            int slashIndex = docId.indexOf("/");
            String rootId = slashIndex == -1 ? docId : docId.substring(0, slashIndex);
            if (!rootId.startsWith(ROOT_ID_PREFIX)) return null;
            String pkgName = rootId.substring(ROOT_ID_PREFIX.length());
            try {
                PackageInfo pkg = getContext().getPackageManager().getPackageInfo(pkgName, 0);
                if (pkg != null && SHARED_UID.equals(pkg.sharedUserId)) {
                    File baseDir = new File(pkg.applicationInfo.dataDir);
                    return slashIndex == -1 ? baseDir : new File(baseDir, docId.substring(slashIndex + 1));
                }
            } catch (PackageManager.NameNotFoundException e) {
                e.printStackTrace();
            }
            return null;
        }
        private int getFlags(File file) {
            if (file.isDirectory()) {
                return DocumentsContract.Document.FLAG_DIR_SUPPORTS_CREATE |
					DocumentsContract.Document.FLAG_SUPPORTS_DELETE |
					DocumentsContract.Document.FLAG_SUPPORTS_RENAME;
            } else {
                return DocumentsContract.Document.FLAG_SUPPORTS_WRITE |
					DocumentsContract.Document.FLAG_SUPPORTS_DELETE |
					DocumentsContract.Document.FLAG_SUPPORTS_RENAME;
            }
        }
        @Override
        public Cursor queryDocument(String documentId, String[] projection) {
            MatrixCursor result = new MatrixCursor(projection != null ? projection : new String[]{
													   DocumentsContract.Document.COLUMN_DOCUMENT_ID,
													   DocumentsContract.Document.COLUMN_DISPLAY_NAME,
													   DocumentsContract.Document.COLUMN_MIME_TYPE,
													   DocumentsContract.Document.COLUMN_FLAGS,
													   DocumentsContract.Document.COLUMN_SIZE,
													   DocumentsContract.Document.COLUMN_LAST_MODIFIED
												   });
            File file = getFileFromDocId(documentId);
            if (file != null && file.exists()) {
                result.newRow().add(DocumentsContract.Document.COLUMN_DOCUMENT_ID, documentId)
                    .add(DocumentsContract.Document.COLUMN_DISPLAY_NAME, file.getName())
                    .add(DocumentsContract.Document.COLUMN_MIME_TYPE, file.isDirectory() ? 
						 DocumentsContract.Document.MIME_TYPE_DIR : getMimeType(file))
                    .add(DocumentsContract.Document.COLUMN_FLAGS, getFlags(file))
                    .add(DocumentsContract.Document.COLUMN_SIZE, file.length())
                    .add(DocumentsContract.Document.COLUMN_LAST_MODIFIED, file.lastModified());
            }
            return result;
        }
        @Override
        public Cursor queryChildDocuments(String parentDocumentId, String[] projection, String sortOrder) {
            MatrixCursor result = new MatrixCursor(projection != null ? projection : new String[]{
													   DocumentsContract.Document.COLUMN_DOCUMENT_ID,
													   DocumentsContract.Document.COLUMN_DISPLAY_NAME,
													   DocumentsContract.Document.COLUMN_MIME_TYPE,
													   DocumentsContract.Document.COLUMN_FLAGS,
													   DocumentsContract.Document.COLUMN_SIZE,
													   DocumentsContract.Document.COLUMN_LAST_MODIFIED
												   });
            File parent = getFileFromDocId(parentDocumentId);
            if (parent != null && parent.isDirectory()) {
                File[] files = parent.listFiles();
                if (files != null) {
                    for (File file : files) {
                        String childId = parentDocumentId + "/" + file.getName();
                        result.newRow().add(DocumentsContract.Document.COLUMN_DOCUMENT_ID, childId)
                            .add(DocumentsContract.Document.COLUMN_DISPLAY_NAME, file.getName())
                            .add(DocumentsContract.Document.COLUMN_MIME_TYPE, file.isDirectory() ? 
								 DocumentsContract.Document.MIME_TYPE_DIR : getMimeType(file))
                            .add(DocumentsContract.Document.COLUMN_FLAGS, getFlags(file))
                            .add(DocumentsContract.Document.COLUMN_SIZE, file.length())
                            .add(DocumentsContract.Document.COLUMN_LAST_MODIFIED, file.lastModified());
                    }
                }
            }
            return result;
        }
        @Override
        public ParcelFileDescriptor openDocument(String documentId, String mode, CancellationSignal signal) 
		throws FileNotFoundException {
            File file = getFileFromDocId(documentId);
            if (file == null || !file.exists() || file.isDirectory()) {
                throw new FileNotFoundException(documentId);
            }
            return ParcelFileDescriptor.open(file, ParcelFileDescriptor.parseMode(mode));
        }
        @Override
        public String createDocument(String parentDocumentId, String mimeType, String displayName) 
		throws FileNotFoundException {
            File parent = getFileFromDocId(parentDocumentId);
            if (parent == null || !parent.isDirectory()) {
                throw new FileNotFoundException(parentDocumentId);
            }
            File file = new File(parent, displayName);
            try {
                if (DocumentsContract.Document.MIME_TYPE_DIR.equals(mimeType)) {
                    file.mkdirs();
                } else {
                    file.createNewFile();
                }
                return parentDocumentId + "/" + displayName;
            } catch (IOException e) {
                throw new FileNotFoundException(e.getMessage());
            }
        }
        @Override
        public void deleteDocument(String documentId) throws FileNotFoundException {
            File file = getFileFromDocId(documentId);
            if (file == null || !file.exists()) {
                throw new FileNotFoundException(documentId);
            }
            deleteRecursive(file);
        }
        @Override
        public String renameDocument(String documentId, String displayName) throws FileNotFoundException {
            File oldFile = getFileFromDocId(documentId);
            if (oldFile == null || !oldFile.exists()) {
                throw new FileNotFoundException(documentId);
            }
            File newFile = new File(oldFile.getParent(), displayName);
            if (oldFile.renameTo(newFile)) {
                String parentId = documentId.substring(0, documentId.lastIndexOf("/") + 1);
                return parentId + displayName;
            }
            throw new FileNotFoundException("Rename failed");
        }
        private void deleteRecursive(File file) {
            if (file.isDirectory()) {
                File[] children = file.listFiles();
                if (children != null) {
                    for (File child : children) {
                        deleteRecursive(child);
                    }
                }
            }
            file.delete();
        }
        private String getMimeType(File file) {
            String fileName = file.getName();
            int dotIndex = fileName.lastIndexOf(".");
            if (dotIndex == -1) return "application/octet-stream";
            String ext = fileName.substring(dotIndex + 1).toLowerCase();
            String mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext);
            return mime != null ? mime : "application/octet-stream";
        }
    }
}

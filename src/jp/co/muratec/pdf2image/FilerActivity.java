package jp.co.muratec.pdf2image;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;

import com.artifex.mupdf.MuPDFActivity;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.AdapterView.OnItemClickListener;

public class FilerActivity extends Activity {
	protected static final int ICON_WIDTH  = 64;	// アイコンの幅
	protected static final int ICON_HEIGHT = 72;	// アイコンの高さ
	
	private static final int DIALOG_ID_CHOOSE_ACTION = 10;
	private static final int REQUEST_CODE_SRCDIR = 1;	// フォルダ選択：ソース
	private int mode;
	
	
	private ListView listView;
	private File     currentFile = null;
	private String   chooseFile  = null;
	private static ArrayList<File> arr_files;
	public boolean mBusy;
	
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.pdf2image_filer);
        
        // intent
		Intent intent = getIntent();
		if(intent != null){
			mode = intent.getIntExtra("mode", REQUEST_CODE_SRCDIR);
			if(mode == REQUEST_CODE_SRCDIR){
				// ソース選択の場合
				//	ファイルを直接指定するので、フォルダ選択ボタンは非表示にする
				((Button)findViewById(R.id.submitButton)).setVisibility(View.GONE);
			}
			currentFile = new File(intent.getStringExtra("openPath"));
        
			listView = (ListView)findViewById(R.id.listview);
			listView.setOnItemClickListener(itemClickListener);			// リストからファイル/フォルダをタップした時の処理

			setListData(currentFile.getAbsolutePath());					// ファイル一覧取得
		}
    }
    
    // リスト生成
    protected void setListData(String path) {
        arr_files = new ArrayList<File>();
        
        File[] files = null;
        File dir     = null;
        if(path != null){
        	dir = new File(path);
            files = dir.listFiles();
        }
        
        // ファイルの一覧を取得
        if(files == null){
        	// フォルダが開けない
        	if(currentFile == null || currentFile.getAbsolutePath() == path){
        		// 現在位置==開きたいパス：SDがマウントされてない等で初期フォルダを開けない
        		path = "/";								// ルートで代用
        	}else{
        		// フォルダの移動に失敗（移動先フォルダに閲覧権限がない等）
        		path = currentFile.getAbsolutePath();	// 移動前の位置に戻す
        	}
        	dir = new File(path);
        	files = dir.listFiles();
        	if (files == null) {
        		// 念のため、さらに代用位置でも駄目なら
            	Toast.makeText(FilerActivity.this, getString(R.string.fileractivity_warning_text1), Toast.LENGTH_SHORT).show();
        		return;
        	}
        	Toast.makeText(FilerActivity.this, getString(R.string.fileractivity_warning_text2,path), Toast.LENGTH_SHORT).show();
        }
        
        // ソート
        Arrays.sort(files, new FileSort());
		// 先頭にフォルダをまとめる
		for(int i = 0 ; i<files.length ; i++){
			if(files[i].isDirectory()){
				arr_files.add(files[i]);
			}
		}
		// フォルダの後にファイル
		for(int i = 0 ; i<files.length ; i++){
			if(!files[i].isDirectory()){
				arr_files.add(files[i]);
			}
		}
		
        TextView path_field = (TextView)this.findViewById(R.id.currentPath);
        path_field.setText(path);
        currentFile = dir;
        
		listView.setAdapter(null);
		FileListItemAdapter arrayAdapter = new FileListItemAdapter(FilerActivity.this,arr_files);
		listView.setAdapter(arrayAdapter);
		listView.invalidate();
        
        return;
    };
    
    // ファイル一覧ソート用Comparator
    static class FileSort implements Comparator<File>{
    	public int compare(File src, File target){
	    	int diff = src.getName().compareToIgnoreCase(target.getName());
	    	return diff;
	    }
    }
    
    /*
     * ボタン類
     */
    // 上へボタン
    public void moveDirUp(View v){
//    	Toast.makeText(this,currentFile.getParent(),Toast.LENGTH_SHORT).show();
		setListData(currentFile.getParent());
    }

    // 出力先フォルダを選択してメイン画面に戻る
    public void setResult(View v){
    	Intent retIntent = new Intent();
    	retIntent.putExtra("path", currentFile.getAbsolutePath());
    	setResult(RESULT_OK,retIntent);
    	finish();
    }
    
    // リストのアイテムを短押しした時の処理
    private OnItemClickListener itemClickListener = new OnItemClickListener() {
		@SuppressWarnings("deprecation")
		public void onItemClick(AdapterView<?> parent, View v, int pos, long id) {
			ListView lv = (ListView) parent;
			File item = (File) lv.getItemAtPosition(pos);
			if(item.isDirectory()){
				// フォルダなら移動する（選択先フォルダでリストの再描画）
				setListData(item.getAbsolutePath());
			} else if(    mode == REQUEST_CODE_SRCDIR
					&& (  item.getName().endsWith(".pdf")
		    		   || item.getName().endsWith(".xps")
		    		   || item.getName().endsWith(".cbz"))
		    		){
				// ソース選択時、対象ファイルだったら
				chooseFile = item.getAbsolutePath();
				showDialog(DIALOG_ID_CHOOSE_ACTION);
			}
		}
	};
	
	// リストのアダプタ（１件分の表示）
	public class FileListItemAdapter extends ArrayAdapter<File> {
		private LayoutInflater myInflater;
		
		public FileListItemAdapter(Context context,ArrayList<File> files) {
			super(context, 0, files);
			myInflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		}

		@Override
		public View getView(int position, View convertView, ViewGroup parent) {
			if (convertView == null ){
				convertView = myInflater.inflate(R.layout.pdf2image_filer_item, null);
			}
			File filedata = this.getItem(position);
			
			// ファイル名
			boolean dirFlag = false;
			String viewName = filedata.getName();
			if(filedata.isDirectory()){
				viewName += "/";		// フォルダには印をつけておく
				dirFlag = true;
			}
			TextView filename_field = (TextView)convertView.findViewById(R.id.filename);
			filename_field.setText(viewName);
			
			// ファイルサイズ
			TextView filesize_field = (TextView)convertView.findViewById(R.id.fileSize);
			filesize_field.setText(String.format("%1$,3d",(filedata.length()/1024+1))+"KB");
			
			// 更新日時
			Date lastModifiedDate = new Date(filedata.lastModified());
			String lastModifiedDateStr = new SimpleDateFormat("yyyy-MM-dd hh:mm:ss").format(lastModifiedDate);
			TextView modified_field = (TextView)convertView.findViewById(R.id.fileModified);
			modified_field.setText(lastModifiedDateStr);
			
			// アイコン
			ImageView imageView = (ImageView)convertView.findViewById(R.id.imageListThumbnail);
			String strfilename = filedata.getName();
			if(strfilename.endsWith(".pdf")
			|| strfilename.endsWith(".xps")
			|| strfilename.endsWith(".cbz")
			){
				// PDF
				imageView.setImageResource(R.drawable.pdf);
			} else if(dirFlag){
				// フォルダ
				imageView.setImageResource(R.drawable.folder);	// フォルダアイコンに
				filesize_field.setText("");						// フォルダは容量非表示
			} else {
				// その他のファイル
				imageView.setImageResource(R.drawable.file);
			}
			return convertView;
		}
	}

	protected Dialog onCreateDialog(int id){
    	switch (id) {
		case DIALOG_ID_CHOOSE_ACTION:
			// PDFを選択した際のアクション
	    	return new AlertDialog.Builder(this)
	    	.setTitle(getString(R.string.choose_title))
	    	.setMessage(getString(R.string.choose_msg))
	    	.setPositiveButton(getString(R.string.choose_specification), new DialogInterface.OnClickListener() {
	    		// 変換対象に指定
				public void onClick(DialogInterface dialog, int whichButton) {
			    	Intent retIntent = new Intent();
			    	retIntent.putExtra("path", currentFile.getAbsolutePath());
			    	retIntent.putExtra("filepath", chooseFile);
			    	setResult(RESULT_OK,retIntent);
			    	finish();
	            }
	        })
	        .setNeutralButton(getString(R.string.choose_view), new DialogInterface.OnClickListener() {
				// 直接閲覧する
				public void onClick(DialogInterface dialog, int which) {
					Uri uri = Uri.parse(chooseFile);
					Intent intent = new Intent(FilerActivity.this,MuPDFActivity.class);
					intent.setAction(Intent.ACTION_VIEW);
					intent.setData(uri);
					startActivity(intent);
				}
			})
	        .setNegativeButton(getString(R.string.cancel_button), new DialogInterface.OnClickListener() {
	            public void onClick(DialogInterface dialog, int whichButton) {
	            }
	        })
	    	.create();
    	}
		return null;

	}

}

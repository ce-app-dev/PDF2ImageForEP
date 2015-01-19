/*
 * PDFを画像ファイルに変換するアプリです
 * PDFの処理に関しては、MuPDFのソースをそのまま利用しています
 * http://mupdf.com
 *
 * 従って、本アプリケーションのライセンスもGPLv3となります
 */

package jp.co.muratec.pdf2image;


import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;

import com.artifex.mupdf.MuPDFActivity;
import com.artifex.mupdf.MuPDFCore;
import com.artifex.mupdf.SafeAsyncTask;

import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.text.InputType;
import android.view.Display;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.graphics.Bitmap;
import android.graphics.PointF;
import android.graphics.Bitmap.CompressFormat;
import android.util.Log;

import jp.co.muratec.pdfdrawerdata.DrawerData;

public class PDF2ImageActivity extends Activity {
	private static final int REQUEST_CODE_SRCFILE = 1;	// フォルダ選択：ソース
	private static final int REQUEST_CODE_DSTDIR  = 2;	// フォルダ選択：出力先
	private static final int REQUEST_CODE_PREVIEW  = 3;	// PDFの一覧を表示
	
	private static final int DIALOG_ID_ALERT_SRC	  = 1;	// ダイアログ管理番号（ソースに対象ファイルが無い）
	private static final int DIALOG_ID_ALERT_DST	  = 2;	// ダイアログ管理番号（出力先に書き込めない）
	private static final int DIALOG_ID_OUTPUT_SETTING = 3;	// ダイアログ管理番号（出力設定）
	private static final int DIALOG_ID_PROGRESS	   = 4;	// ダイアログ管理番号（プログレス）
	private static final int DIALOG_ID_PASSWORD	   = 5;	// ダイアログ管理番号（パスワード入力）
	private static final int DIALOG_ID_PASSWORD_ERROR = 6;	// ダイアログ管理番号（パスワード入力エラー）
	
	private static String TAG = "PDF2Image";
	private TextView srcDirView,dstDirView;		// 選択したフォルダの表示先
	private String srcDirPath,dstDirPath;		// 選択したフォルダ
	private String srcFilePath = null;			// ソースファイル
	private EditText passwordView;				// パスワード入力フォーム
	private boolean passwordDialogFlag = false;	// パスワード入力失敗時の表示切り替え用フラグ
	private Intent filerIntent;					// フォルダ選択用Intent
	
	// 出力設定
	private AlertDialog editDialog;						// 出力設定ダイアログ
	private TextView	page_info_view;					// ページ情報
	private TextView	page_start_view;				// ページ指定：開始
	private int		 page_start_value = 1;			// ページ指定：開始
	private TextView	page_end_view;					// ページ指定：終了
	private int		 page_end_value = -1;			// ページ指定：終了
	private DeepRadioGroup  size_group_view;				// サイズ選択（ラジオボタン）
	private int		 size_group_value = 0;			// 同選択値
	private TextView	custom_width_view;				// エディットテキスト（カスタムサイズ：幅）
	private int		 custom_width_value = -1;		// 同入力値
	private TextView	custom_height_view;				// エディットテキスト（カスタムサイズ：高さ）
	private int		 custom_height_value = -1;		// 同入力値
	private Spinner	 format_spinner_view;			// フォーマット選択（スピナー）
	private int		 format_spinner_value = 0;		// 同選択値
	private TextView	quality_view;					// 画質
	private int		 quality_value = 100;			// 同入力値
	
	ProgressDialog progressBar;					// 変換作業の進捗表示用プログレスバー
	private int processingCount = 0;			// 変換作業の進捗管理用カウンター
	private int processingMax   = 0;			// 変換作業の進捗最大値
	private boolean processingFlag = false;		// 変換処理の実行中はtrueに
	private int processingSuccessFlag = RESULT_FIRST_USER;// 変換処理が成功したか否か
	
	private MuPDFCore core = null;
	
	private DrawerData draw = null;
	private boolean isIntentStart = false;
	
	//xujie debug
	private boolean isGetPage = false;
	//xujie debug
	
    
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		Log.d(TAG, "onCreate()");
		// 待ち受け中のダイヤログ表示中に回転によるアクティビティの再起動が発生すると困るので固定しておく。
		//this.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_NOSENSOR);
		
		// 待ち受け状態のまま放置してしまうことを防ぐために画面をスリープさせない。
		//getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

		// Activityのタイトル表示なし
		requestWindowFeature(Window.FEATURE_NO_TITLE);
		
		// 他アプリからClassLoader経由で呼ばれる場合に備え、ライブラリ読み込みクラスを使うように変更したため、自アプリで使う時は事前準備が必要
		LoadLibrary instance = LoadLibrary.getInstance();
		try {
			instance.setAppPath(getPackageManager().getApplicationInfo("jp.co.muratec.pdf2image", 0).sourceDir , getFilesDir().getAbsolutePath());
			instance.init();
		} catch (NameNotFoundException e) {
			// TODO 自動生成された catch ブロック
			e.printStackTrace();
		}

		//xujie debug
		String pdf_name_for_preview= null;//PDFファイルPreviewため
		String pdf_name_for_get_pages= null;//PDFファイルページ獲得ため
		boolean isSetPreviewFinish = false;
		//luo
		boolean isMobilePhone = false;
		//luo
		try {
			Intent intent = getIntent();						
			pdf_name_for_preview = intent.getStringExtra("PDF_NAME_FOR_PREVIEW");
			pdf_name_for_get_pages = intent.getStringExtra("PDF_NAME_FOR_GET_PAGES");
			isSetPreviewFinish = intent.getBooleanExtra("IS_SET_PREVIEW_FINISH", false);
			//luo
			isMobilePhone = intent.getBooleanExtra("IS_MOBILE_PHONE", false);
			//luo
		}
		catch (Exception e) {
			e.printStackTrace();
		}
		if(pdf_name_for_preview != null){//PDFファイルPreviewため			
			Uri uri = Uri.parse(pdf_name_for_preview);
			Intent intent = new Intent(PDF2ImageActivity.this,MuPDFActivity.class);
			intent.setAction(Intent.ACTION_VIEW);
			intent.setData(uri);
			//luo
			intent.putExtra("IS_MOBILE_PHONE", isMobilePhone);
			//luo
			intent.putExtra("IS_FROM_MOBILEPRINT_PREVIEW", true);
			//startActivityForResult(intent, REQUEST_CODE_PREVIEW);
			startActivity(intent);
			finish();
			return;
		}	
		if(pdf_name_for_get_pages != null){//PDFファイルページ獲得ため		
			isGetPage = true;
			int pdfPages = getPDFPages(pdf_name_for_get_pages);
			if (isSetPreviewFinish) {
				if (!MuPDFActivity.mPreviewActivity.isFinishing()) {
					MuPDFActivity.mPreviewActivity.finish();
				}
			}
			Intent intent = new Intent();
			intent.putExtra("PDF_PAGES", pdfPages);
			setResult(RESULT_OK, intent); 
			finish();
			isGetPage = false;
			return;
		}
		if (isSetPreviewFinish) {
			if (!MuPDFActivity.mPreviewActivity.isFinishing()) {
				MuPDFActivity.mPreviewActivity.finish();
			}
			finish();
			return;
		}
		//xujie debug
		
		// Intent を受け取る
		draw = (DrawerData)getIntent().getSerializableExtra("DrawerData");
		// Intentを受け取れた場合(他アプリからの起動)
		if (draw != null) {
			// 待ち受け中のダイヤログ表示中に回転によるアクティビティの再起動が発生すると困るので固定しておく。
			//this.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_NOSENSOR);
			
			// 待ち受け状態のまま放置してしまうことを防ぐために画面をスリープさせない。
			getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
			
			// 終了要求がされていれば過去のファイルを消して終了
			if (draw.getFinishApp()) {
				for (int m = draw.getStartPage(); m < draw.getEndPage() + 1; m++){
					draw.deleteFile(m);
				}
				finish();
				return;
			}
			isIntentStart = true;
			srcFilePath = draw.getDataPath();
			Log.d(TAG, "srcFilePath:" + srcFilePath);
			Convert();
			processingSuccessFlag = RESULT_FIRST_USER;
			doConvert();
			Log.d(TAG, "debug3");
			// コンバート処理が終了するまで待つ。
//			while (processingSuccessFlag == RESULT_FIRST_USER) {
//				try {
//					Thread.sleep(200);
//				} catch (InterruptedException e) {
//					e.printStackTrace();
//				}
//			} 

			// 呼び出し元に処理結果を返す
			Intent intent = new Intent();

			if (processingSuccessFlag == RESULT_OK) {
				intent.putExtra("DrawerData", draw);  
				setResult(RESULT_OK,intent);  
			}
			else {
				setResult(RESULT_CANCELED,intent);  
			}
			finish();
		}
		// アプリの単独起動
		else {
			setContentView(R.layout.activity_main);
			isIntentStart = false;
	        // TODO 前回のフォルダを復元：現在はSDルート固定で手抜き
	        srcDirView = (TextView)this.findViewById(R.id.srcPath);
	        srcDirPath = Environment.getExternalStorageDirectory().getAbsolutePath();
	        srcDirView.setText(getString(R.string.info_src_file_path).toString());
	        
	        dstDirView = (TextView)this.findViewById(R.id.dstPath);
	        dstDirPath = Environment.getExternalStorageDirectory().getAbsolutePath();
	        dstDirView.setText(dstDirPath);
	        
	        filerIntent = new Intent(this, jp.co.muratec.pdf2image.FilerActivity.class);	// ファイル選択用Intentを準備
		}
	}
	@Override
	public boolean dispatchKeyEvent(KeyEvent event) { 
		if (event.getAction()==KeyEvent.ACTION_DOWN) { 
			switch (event.getKeyCode()) { 
				case KeyEvent.KEYCODE_BACK:
					// 戻るボタン
					processingFlag = false;		// スレッドを停止（念のため）
					break;
			}
		}
		return super.dispatchKeyEvent(event);
	}
	
	/*
	 * ボタン類
	 */
	// 変換対象フォルダ選択
	public void onChangeSrc(View v){
		filerIntent.putExtra("openPath"   , srcDirPath);
		filerIntent.putExtra("mode"	   , REQUEST_CODE_SRCFILE);
		startActivityForResult(filerIntent, REQUEST_CODE_SRCFILE );
	}
	
	// 出力先フォルダ選択
	public void onChangeDst(View v){
		filerIntent.putExtra("openPath"   , dstDirPath);
		filerIntent.putExtra("mode"	   , REQUEST_CODE_DSTDIR);
		startActivityForResult(filerIntent, REQUEST_CODE_DSTDIR );
	}
	
	// フォルダ選択画面からの戻り
	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		super.onActivityResult(requestCode, resultCode, data);
		if(resultCode == Activity.RESULT_OK){
			if(requestCode == REQUEST_CODE_SRCFILE){
				// ソース選択からの戻り
				srcDirPath = data.getStringExtra("path");
				srcFilePath = data.getStringExtra("filepath");
				srcDirView.setText(srcFilePath);
				// ファイルが変更されたので、ページ指定はリセット
				page_start_value =  1;
				page_end_value   = -1;
			} else if (requestCode == REQUEST_CODE_DSTDIR) {
				// 出力先選択からの戻り
				dstDirPath = data.getStringExtra("path");
				dstDirView.setText(dstDirPath);
			}
//			//xujie debug
//			else if (requestCode == REQUEST_CODE_PREVIEW) {
//				int pdfPages = data.getIntExtra("PDF_PAGES", 1);
//				
//				Intent intent = new Intent();
//				intent.putExtra("PDF_PAGES", pdfPages);
//				setResult(RESULT_OK, intent); 
//				finish();
//			}
//			//xujie debug
		}
//		//xujie debug
//		else if (resultCode == Activity.RESULT_CANCELED) {
//			if (requestCode == REQUEST_CODE_PREVIEW) {
//				setResult(RESULT_CANCELED); 
//				finish();
//			}
//		}
//		else if (resultCode == MuPDFActivity.RESULT_BACK) {
//			if (requestCode == REQUEST_CODE_PREVIEW) {
//				setResult(MuPDFActivity.RESULT_BACK); 
//				finish();
//			}
//		}
//		//xujie debug
	}
	
	// 変換実行
	@SuppressWarnings("deprecation")
	public void onExecConvert(final View v){
		Convert();
	}
	
	// 変換実行
	public void Convert(){
		// ソースの確認
		if(srcFilePath == null
		|| (  !srcFilePath.toLowerCase().endsWith(".pdf")
		   && !srcFilePath.toLowerCase().endsWith(".xps")
		   && !srcFilePath.toLowerCase().endsWith(".cbz")
		   )
		){
			// 処理可能なファイルがない
			showDialog(DIALOG_ID_ALERT_SRC);
			return;
		}
	
		if (isIntentStart == false) {	// アプリの単独起動の場合	
			// 出力先の確認
			File outDir = new File(dstDirPath);
			if(outDir == null || outDir.canWrite() == false){
				// 書き込めない
				showDialog(DIALOG_ID_ALERT_DST);
				return;
			}
		}		
		// ページ数等を得るため、ページを開く
		openPDF(srcFilePath,"");
	}
	
	// PDFファイルを開く
	@SuppressWarnings("deprecation")
	private void openPDF(String src,String password){
		try {
			core = new MuPDFCore(srcFilePath);
			if (core != null && core.needsPassword()) {
				if(core.authenticatePassword(password) != true){
					// パスワード違い
					if(passwordDialogFlag){
						showDialog(DIALOG_ID_PASSWORD_ERROR);
					} else {
						// パスワード入力
						showDialog(DIALOG_ID_PASSWORD);
					}
					return;
				}
				passwordDialogFlag = false;
			}
			if(core != null && isIntentStart == false){
				// 出力設定ダイアログ
				showDialog(DIALOG_ID_OUTPUT_SETTING);
			}
			if (core != null) {
				core.setRenderResolution(320);
			}

		} catch (Exception e) {
			// TODO 自動生成された catch ブロック
			e.printStackTrace();
		}
	}
	
	// ダイアログ管理
	@Override
	protected Dialog onCreateDialog(int id){
		switch (id) {
		case DIALOG_ID_ALERT_SRC:
			// 処理可能なファイルがない
			return new AlertDialog.Builder(this)
			.setTitle(getString(R.string.warning_dialog_title_src))
			.setMessage(getString(R.string.warning_dialog_message_src))
			.create();
		case DIALOG_ID_ALERT_DST:
			// 書き込めない
			return new AlertDialog.Builder(this)
			.setTitle(getString(R.string.warning_dialog_title_dst))
			.setMessage(getString(R.string.warning_dialog_message_dst))
			.create();
		case DIALOG_ID_OUTPUT_SETTING:
			// 出力設定
			LayoutInflater factory = LayoutInflater.from(this); 
			View editView = factory.inflate(R.layout.pdf2image_output, null);		// カスタムダイアログ用レイアウト
			
			// 各入力要素View
			page_info_view	  = (TextView)editView.findViewById(R.id.page_info);		// ページ情報
			page_start_view	 = (TextView)editView.findViewById(R.id.page_start);		// 出力ページ：開始
			page_end_view	   = (TextView)editView.findViewById(R.id.page_end);		// 出力ページ：終了
			size_group_view	 = (DeepRadioGroup)editView.findViewById(R.id.size_group);	// サイズ選択肢
			custom_width_view   = (TextView)editView.findViewById(R.id.custom_width);	// カスタムサイズ：幅
			custom_height_view  = (TextView)editView.findViewById(R.id.custom_height);	// カスタムサイズ：高さ
			format_spinner_view = (Spinner)editView.findViewById(R.id.format);			// 出力フォーマット選択肢
			quality_view		= (TextView)editView.findViewById(R.id.quality);		// 画質
			
			//ダイアログの作成(AlertDialog.Builder) 
			editDialog = new AlertDialog.Builder(this) 
				.setIcon(android.R.drawable.ic_dialog_alert) 
				.setTitle(getString(R.string.output_setting_title).toString()) 
				.setView(editView) 
				.setPositiveButton(getString(R.string.execution_button).toString(), new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which) {
						// 変換処理
						doConvert();
					}
				})
				.create();
			return editDialog;
		case DIALOG_ID_PROGRESS:
			//  プログレスバー
			progressBar = new ProgressDialog(this);
			progressBar.setTitle(getString(R.string.progress_dialog_title)); 
			progressBar.setMessage(getString(R.string.progress_dialog_title)+"..."); 
			progressBar.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
			progressBar.setCancelable(true);
			progressBar.setOnCancelListener(new DialogInterface.OnCancelListener() {
				public void onCancel(DialogInterface dialog) {
					processingFlag = false;		// プログレスバーがキャンセルされたらスレッドも停止する
				}
			});
			return progressBar;
		case DIALOG_ID_PASSWORD:
			passwordView = new EditText(this);
			passwordView.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);

			return new AlertDialog.Builder(this)
				.setIcon(android.R.drawable.ic_dialog_info)
				.setTitle(getString(R.string.input_password))
				.setView(passwordView)
				.setPositiveButton("OK", new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int whichButton) {
						passwordDialogFlag = true;
						String input = passwordView.getText().toString();
						dialog.dismiss();
						openPDF(srcFilePath,input);
					}
				})
				.setNegativeButton(getString(R.string.cancel_button), new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int whichButton) {
					}
				})
				.create();
		case DIALOG_ID_PASSWORD_ERROR:
			return new AlertDialog.Builder(this)
			.setTitle(getString(R.string.password_error_title))
			.setMessage(getString(R.string.password_error_msg))
			.setPositiveButton(getString(R.string.input_password_retry), new DialogInterface.OnClickListener() {
				@SuppressWarnings("deprecation")
				public void onClick(DialogInterface dialog, int whichButton) {
					showDialog(DIALOG_ID_PASSWORD);
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
	
	@SuppressWarnings("deprecation")
	protected void onPrepareDialog(int id, Dialog dialog,Bundle data) {
		switch (id) {
		case DIALOG_ID_OUTPUT_SETTING:
			// 出力設定ダイアログ
			if(core == null){
				// 縦横回転とかで来た場合、coreの再取得が必要
				return;
			}
			// ページ指定
			int page_max = core.countPages();
			page_info_view.setText(getString(R.string.output_setting_page_info,page_max));
			if(page_end_value == -1){
				page_end_value = page_max;
			}
			page_start_view.setText(Integer.toString(page_start_value));
			page_end_view.setText(Integer.toString(page_end_value));
			
			// サイズ指定の選択肢
			size_group_view.check(size_group_view.getChildAt(size_group_value).getId());
			// カスタムサイズ初期値
			if(custom_width_value == -1 || custom_height_value == -1){
				Display display = getWindowManager().getDefaultDisplay();
				custom_width_value  = display.getWidth();
				custom_height_value = display.getHeight();
			}
			custom_width_view.setText(Integer.toString(custom_width_value));
			custom_height_view.setText(Integer.toString(custom_height_value));
			// 出力フォーマット
			format_spinner_view.setSelection(format_spinner_value);
			// 画質
			quality_view.setText(Integer.toString(quality_value));

			break;
		case DIALOG_ID_PROGRESS:
			progressBar.setProgress(0);
			progressBar.setMax(processingMax);
			break;
		case DIALOG_ID_PASSWORD:
		case DIALOG_ID_PASSWORD_ERROR:
			passwordDialogFlag = false;
			
		default:
			super.onPrepareDialog(id, dialog,data);
			
		}
	}
	
	// 変換処理
	@SuppressWarnings("deprecation")
	protected void doConvert(){
		// 変換準備：各種設定初期化
		
		// ページ
		if (isIntentStart == false) {	// アプリの単独起動の場合	
			doConvertAsBackGround();
		}
		else {
			// 開始ページ・終了ページ
			String input_page_start = "";
			String input_page_end = "";
			page_start_value = draw.getStartPage();
			page_end_value = draw.getEndPage();
			if (page_start_value == 0) {
				page_start_value = 1;
				page_end_value = 1;
			}
			// サイズ
			size_group_value = 0;	// PDF設定準拠
			// フォーマット
			format_spinner_value = 0;
			// 画質
			String input_quality = "";

			try {
				int page_max = core.countPages();						// ページ数
				int page_column = Integer.toString(page_max).length();	// ページ数の桁数

				// 出力ファイル名生成準備（出力ファイル名：ソースファイル名_pageページ数.(jpg|png)）
				String fileName = (new File(srcFilePath)).getName();	// 作業対象PDFのファイル名部分
				String baseFileName = fileName;
				int index = fileName.lastIndexOf(".");
				if(index >= 0){
					baseFileName = fileName.substring(0, index);		// 拡張子を除外
				}
				
				// 処理ループ
				PointF size = new PointF();
				for(int i = page_start_value - 1;i<page_end_value;i++){
					// 出力サイズ
					if(size_group_value == 1){
						// カスタムの場合
						size.set((float)custom_width_value, (float)custom_height_value);
					} else {
						// PDF設定に従う
						size = core.getPageSize(i);
					}
					Log.d(TAG, "PDF Size:X=" + size.x + "  :Y=" + size.y);
					// 読み込めるサイズか
					double MemorySize = size.x * size.y;						// 画像のサイズ
					double freeSize   = (Runtime.getRuntime().freeMemory());	// 現在のフリーメモリ
					Log.d(TAG, "Memory:" + MemorySize + "  freeSize:" + freeSize);
					Bitmap bm = core.drawPage(i,(int)size.x, (int)size.y, 0, 0, (int)size.x, (int)size.y);	// PDFの指定ページを指定サイズでレンダリング

					// レンダリング結果を格納する
					draw.SetBitmap(bm, i + 1, getApplicationContext());
					Log.d(TAG, "Write Done");
					if (bm != null) {
						bm.recycle();
					}
					processingSuccessFlag = RESULT_OK;

						}
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}
		
	private void doConvertAsBackGround(){
		// 変換準備：各種設定初期化
		int page_max = core.countPages();
		// 開始ページ
		String input_page_start = page_start_view.getText().toString();
		if(input_page_start.equals("")){
			page_start_value = 1;				// 未指定は先頭ページ
		} else {
			page_start_value = Integer.parseInt(input_page_start);
			if(page_start_value < 1){
				page_start_value = 1;			// 先頭より小さい指定は先頭に
			} else if (page_start_value > page_max) {
			page_start_value = page_max;	// 最終より大きい場合は最終に
			}
		}
		// 終了ページ
		String input_page_end = page_end_view.getText().toString();
		if(input_page_end.equals("")){
			page_end_value = page_max;			// 未指定は最終ページ
		} else {
			page_end_value = Integer.parseInt(input_page_end);
			if(page_end_value < 1){
				page_end_value = 1;				// 先頭より小さい場合は先頭に
			} else if (page_end_value > page_max) {
				page_end_value = page_max;		// 最終より大きい場合は最終に
			}
		}
		if(page_start_value > page_end_value){
			// 先頭 > 最終 の場合
		//  TODO 警告ダイアログでも出すべき？
			return;
		}

		// サイズ
		size_group_value = 0;	// デフォルトはPDF設定準拠
		if(size_group_view.getCheckedRadioButtonId() == R.id.size_custom){
			// カスタムサイズ
			String input_width  = custom_width_view.getText().toString();
			String input_height = custom_height_view.getText().toString();
			if(!input_width.equals("") && !input_height.equals("")){
				custom_width_value  = Integer.parseInt(input_width);
				custom_height_value = Integer.parseInt(input_height);
				if(custom_width_value > 0 && custom_height_value > 0){
					size_group_value = 1;		// 作成可能サイズが指定されていたらカスタムサイズを使用
				}
			}
		}
		
		// フォーマット
		format_spinner_value = format_spinner_view.getSelectedItemPosition();
		
		// 画質
		String input_quality = quality_view.getText().toString();
		if(input_quality.equals("")){
			quality_value = 100;
		} else {
			quality_value = Integer.parseInt(input_quality);
			if(quality_value < 0){
				quality_value = 0;
			} else if (quality_value > 100) {
				quality_value = 100;
			}
		}
		
		// 変換用バックグラウンド処理
		try {
			SafeAsyncTask<Void,Void,Void> sizingTask = new SafeAsyncTask<Void,Void,Void>() {
				@Override
				protected void onPreExecute() {
					// プログレスバーを準備
					processingCount = 0;
					processingMax   = page_end_value - page_start_value + 1;
					processingFlag  = true;
					showDialog(DIALOG_ID_PROGRESS);
				}
								
				@Override
				protected Void doInBackground(Void... arg0) {
					int page_max = core.countPages();						// ページ数
					int page_column = Integer.toString(page_max).length();	// ページ数の桁数

					// 出力ファイル名生成準備（出力ファイル名：ソースファイル名_pageページ数.(jpg|png)）
					String fileName = (new File(srcFilePath)).getName();	// 作業対象PDFのファイル名部分
					String baseFileName = fileName;
					int index = fileName.lastIndexOf(".");
					if(index >= 0){
						baseFileName = fileName.substring(0, index);		// 拡張子を除外
					}
					
					// 処理ループ
					PointF size = new PointF();
					for(int i = page_start_value - 1;i<page_end_value;i++){
						if(processingFlag == false || isCancelled()){
							// 処理中断
							break;
						}
						
						// 出力サイズ
						if(size_group_value == 1){
							// カスタムの場合
							size.set((float)custom_width_value, (float)custom_height_value);
						} else {
							// PDF設定に従う
							size = core.getPageSize(i);
						}
						Log.d(TAG, "PDF Size:X=" + size.x + "  :Y=" + size.y);
						// 読み込めるサイズか
						double MemorySize = size.x * size.y;						// 画像のサイズ
						double freeSize   = (Runtime.getRuntime().freeMemory());	// 現在のフリーメモリ
						Log.d(TAG, "Memory:" + MemorySize + "  freeSize:" + freeSize);
						if(MemorySize > freeSize){
							// 空きメモリ容量を超えるのでリサンプリングを強制
							double round = Math.sqrt(freeSize / MemorySize);
							size.x  = (float) (round * size.x);
							size.y = (float) (round * size.y);
							Log.d(TAG, "Re Sampling:X=" + size.x + "  :Y=" + size.y);
						}
						Bitmap bm = core.drawPage(i,(int)size.x, (int)size.y, 0, 0, (int)size.x, (int)size.y);	// PDFの指定ページを指定サイズでレンダリング
						try {
							// 画像の保存
							if(format_spinner_value == 0){
								// JPEG版
								FileOutputStream fos = new FileOutputStream(new File(dstDirPath+"/"+baseFileName+"_page"+String.format("%1$0"+page_column+"d", i+1)+".jpg"));
								bm.compress(CompressFormat.JPEG, quality_value, fos);
								fos.close();
							} else {
								// PNG版
								FileOutputStream fos = new FileOutputStream(new File(dstDirPath+"/"+baseFileName+"_page"+String.format("%1$0"+page_column+"d", i+1)+".png"));
								bm.compress(CompressFormat.PNG, quality_value, fos);
								fos.close();
							}
							bm.recycle();
							
							// プログレスバーを更新
							processingCount++;
							publishProgress();
						} catch (FileNotFoundException e) {
							e.printStackTrace();
						} catch (IOException e) {
							e.printStackTrace();
						}
					}
					return null;
				}
		
				@Override
				protected void onProgressUpdate(Void... values){
					progressBar.setProgress(processingCount);
				}
				
				@Override
				protected void onPostExecute(Void result) {
					super.onPostExecute(result);
					if(progressBar != null && progressBar.isShowing()){
						progressBar.dismiss();
					}
				}
				
				@Override
				protected void onCancelled() {
					if(progressBar != null && progressBar.isShowing()){
						progressBar.dismiss();
					}
					super.onCancelled();
				}									
			};
			
			// タスク開始
			sizingTask.safeExecute();
			
		} catch (Exception e) {
			e.printStackTrace();
		}

	}
	
	//xujie debug
	private int getPDFPages(String path) {
		int pages = 1;
		MuPDFCore pdfCore = null;
		try {
			pdfCore = new MuPDFCore(path);
			pages = pdfCore.countPages();
		} catch (Exception e) {
			e.printStackTrace();
		}
		finally {
			if (pdfCore != null)
				pdfCore.onDestroy();
			pdfCore = null;
		}
		return pages;
	}
	
	@Override
	public void onBackPressed() {
		// PDFファイルページ獲得時、Backキー無効
		if (isGetPage) {
			return;
		}
		super.onBackPressed();
	}
	//xujie debug
}

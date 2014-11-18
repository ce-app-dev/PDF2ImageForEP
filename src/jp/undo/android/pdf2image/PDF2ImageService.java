/*
 * PDFを画像ファイルに変換するアプリのプラグイン動作処理です
 * 他のアプリからClassLoader経由で呼び出して使用しする事を想定しています
 * 
 * 
 * PDFの処理に関しては、MuPDFのソースをそのまま利用しています
 * http://mupdf.com
 *
 * 従って、本アプリケーションのライセンスもGPLv3となります
 */

package jp.undo.android.pdf2image;

import java.io.File;
import com.artifex.mupdf.MuPDFCore;

import android.graphics.Bitmap;
import android.graphics.PointF;
import android.os.RemoteException;

public class PDF2ImageService {
	// ライブラリ準備
	public void initLibrary(String appPath,String outPath){
		LoadLibrary instance = LoadLibrary.getInstance();
		instance.init(appPath,outPath);
	}
	
	// 使用できるファイルの種類を判別
	private boolean checkSrcPath(String srcPDFPath){
		if( (new File( srcPDFPath )).exists() == false){
			// srcPathが存在しない
			return false;
		}
		if(!srcPDFPath.endsWith(".pdf")
		&& !srcPDFPath.endsWith(".xps")
		&& !srcPDFPath.endsWith(".cbz")
		){
			// 扱えないファイル
			return false;
		}
		return true;
	}
	
	// パスワードの要・不要
	public int checkNeedsPassword(String srcPDFPath){
		if(checkSrcPath(srcPDFPath) == false){
			return -1;
		}
		try {
			MuPDFCore core = new MuPDFCore(srcPDFPath);
			if(core.needsPassword()){
				return 1;
			} else {
				return 0;
			}
		} catch (Exception e) {
			e.printStackTrace();
			return -1;
		}
	}
	
	// ページ数取得
	public int getCount(String srcPDFPath,String password){
		if(checkSrcPath(srcPDFPath) == false){
			return -1;
		}
		try {
			MuPDFCore core = new MuPDFCore(srcPDFPath);
			if(core.needsPassword()){
				// 要パスワード
				if(core.authenticatePassword(password) != true){
					// パスワード不正
					return -2;
				}
			}
			return core.countPages();
		} catch (Exception e) {
			e.printStackTrace();
			return -1;
		}
	}
	
	// 描画サイズを取得
	public PointF getPDFSize(String srcPDFPath,int page,String password){
		if(checkSrcPath(srcPDFPath) == false){
			return null;
		}
		try {
			MuPDFCore core = new MuPDFCore(srcPDFPath);
			if(core.needsPassword()){
				// 要パスワード
				if(core.authenticatePassword(password) != true){
					// パスワード不正
					return null;
				}
			}
			if(core.countPages() < page){
				// 処理可能ページ数範囲外
				return null;
			}
			
			return core.getPageSize(page-1);
		} catch (Exception e) {
			e.printStackTrace();
			return null;
		}
	}
	
	// 指定ファイルの指定ページをレンダリングして返す
	public Bitmap getImage(String srcPDFPath, int page, int width,int height,String password) throws RemoteException {
		if(page <= 0){
			return null;
		}
		if(checkSrcPath(srcPDFPath) == false){
			return null;
		}
		
		
		try {
			MuPDFCore core = new MuPDFCore(srcPDFPath);
			if(core.needsPassword()){
				// 要パスワード
				if(core.authenticatePassword(password) != true){
					// パスワード不正
					return null;
				}
			}
			if(core.countPages() < page){
				// 処理可能ページ数範囲外
				return null;
			}
			
			PointF size = new PointF();
			// 変換処理
			if(width > 0 && height > 0){
				size.set((float)width, (float)height);
			} else {
				size  = core.getPageSize(page-1);
			}
			double MemorySize = size.x * size.y;						// 画像のサイズ
			double freeSize   = (Runtime.getRuntime().freeMemory());	// 現在のフリーメモリ
			if(MemorySize > freeSize){
				// 空きメモリ容量を超えるのでリサンプリングを強制
				double round = Math.sqrt(freeSize / MemorySize);
				size.x  = (float) (round * size.x);
				size.y = (float) (round * size.y);
			}
			return core.drawPage(page-1,(int)size.x, (int)size.y, 0, 0, (int)size.x, (int)size.y);	// PDFの指定ページを指定サイズでレンダリング
		} catch (Exception e) {
			e.printStackTrace();
			return null;
		}
	}
	
}

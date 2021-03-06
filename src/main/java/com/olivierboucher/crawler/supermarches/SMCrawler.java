package com.olivierboucher.crawler.supermarches;

import com.olivierboucher.crawler.Common;
import com.olivierboucher.crawler.EpicerieCrawler;
import com.olivierboucher.crawler.EpicerieCrawlerJobResult;
import com.olivierboucher.ear.MySQLHelper;
import com.olivierboucher.exception.NetworkErrorException;
import com.olivierboucher.exception.ProductParseException;
import com.olivierboucher.exception.UnrecoverableException;
import com.olivierboucher.model.EpicerieCategory;
import com.olivierboucher.model.EpicerieProduct;
import com.olivierboucher.model.EpicerieStore;
import com.olivierboucher.parser.EpicerieParser;
import com.olivierboucher.parser.supermarches.SMEpicerieParser;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;


public class SMCrawler extends EpicerieCrawler {
	public static final int WEBSITE_ID = 1;

	public SMCrawler(){
		helper = new MySQLHelper();
		products = new ArrayList<EpicerieProduct>();
		try{
			Initialize();
		}
		catch(SQLException e){
			e.printStackTrace();
		}
	}

	@Override
	public EpicerieCrawlerJobResult StartJobMultiThreaded() {
		return null;
	}
	@Override
	public EpicerieCrawlerJobResult StartJob() throws UnrecoverableException {
		// TODO : Verify internet connection
		try {
			if (NeedsUpdate()) {
				for (EpicerieStore store : stores) {
					for (EpicerieCategory category : categories) {
						products.addAll(GetProductsFromCategory(store, category));
					}
				}
				result = Common.CrawlerResult.Complete;
			} else {
				result = Common.CrawlerResult.UpToDate;
			}
			return new EpicerieCrawlerJobResult(products, result);
		} catch (NetworkErrorException e) {
			throw new UnrecoverableException("Could not recover from network excpetion", e);
		}
	}

	private EpicerieProduct GetFirstProductAvailable() throws NetworkErrorException {
		for (EpicerieStore store : stores) {
			for (EpicerieCategory category : categories) {
				List<EpicerieProduct> list = GetProductsFromCategory(store, category);
				if (list.size() > 0) {
					return list.get(0);
				}
			}
		}
		return null;
	}

	private List<EpicerieProduct> GetProductsFromCategory(EpicerieStore store, EpicerieCategory category) throws NetworkErrorException {
		List<EpicerieProduct> list = new ArrayList<EpicerieProduct>();
		try {
			int page = 1;
			Boolean doContinue = true;
			while(doContinue){
				StringBuilder sb = new StringBuilder();
				sb.append("http://www.supermarches.ca/pages/Aubaines.asp?vd=");
				sb.append(store.getSlug());
				sb.append("&cid=");
				sb.append(category.getSlug());
				sb.append("&page=");
				sb.append(page);

				Document doc = Jsoup.connect(sb.toString()).get();

				if(doc.select("tbody tr [onmouseover=this.bgColor = '#FFFFD9']").first() != null){
					Elements elem_items = doc.select("tbody tr [onmouseover=this.bgColor = '#FFFFD9']");
					for(Element element : elem_items){
						EpicerieProduct product = ExtractProduct(element, parser);
						product.setCategory(category);
						product.setStore(store);
						list.add(product);
					}
					page++;
				}
				else{
					doContinue = false;
				}
			}
			return list;
		}
		catch (IOException ioe) {
			throw new NetworkErrorException("", this, category, store, ioe);
		}
	}
	private EpicerieProduct ExtractProduct(Element element, EpicerieParser parser){
		parser.setElement(element);
		try {
			return parser.getProduct();
		} catch (ProductParseException e) {
			return null;
		}
	}
	private void Initialize() throws SQLException{
		parser = new SMEpicerieParser();
		helper.Connect();
		stores = helper.GetStoreList(WEBSITE_ID);
		categories = helper.GetCategoryList(WEBSITE_ID);
		helper.Disconnect();
	}

	private boolean NeedsUpdate() throws NetworkErrorException {
		Date online_date = GetFirstProductAvailable().getRebate().getStart();
		Date db_date = null;
		// Get date from database
		helper.Connect();
		try {
			db_date = helper.SMGetActualStartDate();
		} catch (SQLException e) {
			e.printStackTrace();
		}
		helper.Disconnect();
		// Comparaison
		if(db_date == null || !(db_date.equals(online_date))){
			return true;
		}
		else{
			return false;
		}
	}
	@Override
	public int getWebsiteId(){
		return SMCrawler.WEBSITE_ID;
	}
}

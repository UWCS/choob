package uk.co.uwcs.choob.modules;

import java.io.IOException;
import java.net.URL;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jibble.pircbot.Colors;

import uk.co.uwcs.choob.support.GetContentsCached;

// I'm going to assume that java caches regex stuff.
/**
 * Module providing functionality allowing the BOT to extract information from a website.
 */
public final class ScraperModule {
	private Map <java.net.URL, GetContentsCached>sites=Collections.synchronizedMap(new HashMap<URL, GetContentsCached>()); // URL -> GetContentsCached.
	private final static HashMap <String, Character>EntityMap=new HashMap<String, Character>();

	static {
		// Paste from entitygen.sh.

		EntityMap.put("nbsp", new Character((char)160));
		EntityMap.put("#160", new Character((char)160));
		EntityMap.put("iexcl", new Character((char)161));
		EntityMap.put("#161", new Character((char)161));
		EntityMap.put("cent", new Character((char)162));
		EntityMap.put("#162", new Character((char)162));
		EntityMap.put("pound", new Character((char)163));
		EntityMap.put("#163", new Character((char)163));
		EntityMap.put("curren", new Character((char)164));
		EntityMap.put("#164", new Character((char)164));
		EntityMap.put("yen", new Character((char)165));
		EntityMap.put("#165", new Character((char)165));
		EntityMap.put("brvbar", new Character((char)166));
		EntityMap.put("#166", new Character((char)166));
		EntityMap.put("sect", new Character((char)167));
		EntityMap.put("#167", new Character((char)167));
		EntityMap.put("uml", new Character((char)168));
		EntityMap.put("#168", new Character((char)168));
		EntityMap.put("copy", new Character((char)169));
		EntityMap.put("#169", new Character((char)169));
		EntityMap.put("ordf", new Character((char)170));
		EntityMap.put("#170", new Character((char)170));
		EntityMap.put("laquo", new Character((char)171));
		EntityMap.put("#171", new Character((char)171));
		EntityMap.put("not", new Character((char)172));
		EntityMap.put("#172", new Character((char)172));
		EntityMap.put("shy", new Character((char)173));
		EntityMap.put("#173", new Character((char)173));
		EntityMap.put("reg", new Character((char)174));
		EntityMap.put("#174", new Character((char)174));
		EntityMap.put("macr", new Character((char)175));
		EntityMap.put("#175", new Character((char)175));
		EntityMap.put("deg", new Character((char)176));
		EntityMap.put("#176", new Character((char)176));
		EntityMap.put("plusmn", new Character((char)177));
		EntityMap.put("#177", new Character((char)177));
		EntityMap.put("sup2", new Character((char)178));
		EntityMap.put("#178", new Character((char)178));
		EntityMap.put("sup3", new Character((char)179));
		EntityMap.put("#179", new Character((char)179));
		EntityMap.put("acute", new Character((char)180));
		EntityMap.put("#180", new Character((char)180));
		EntityMap.put("micro", new Character((char)181));
		EntityMap.put("#181", new Character((char)181));
		EntityMap.put("para", new Character((char)182));
		EntityMap.put("#182", new Character((char)182));
		EntityMap.put("middot", new Character((char)183));
		EntityMap.put("#183", new Character((char)183));
		EntityMap.put("cedil", new Character((char)184));
		EntityMap.put("#184", new Character((char)184));
		EntityMap.put("sup1", new Character((char)185));
		EntityMap.put("#185", new Character((char)185));
		EntityMap.put("ordm", new Character((char)186));
		EntityMap.put("#186", new Character((char)186));
		EntityMap.put("raquo", new Character((char)187));
		EntityMap.put("#187", new Character((char)187));
		EntityMap.put("frac14", new Character((char)188));
		EntityMap.put("#188", new Character((char)188));
		EntityMap.put("frac12", new Character((char)189));
		EntityMap.put("#189", new Character((char)189));
		EntityMap.put("frac34", new Character((char)190));
		EntityMap.put("#190", new Character((char)190));
		EntityMap.put("iquest", new Character((char)191));
		EntityMap.put("#191", new Character((char)191));
		EntityMap.put("Agrave", new Character((char)192));
		EntityMap.put("#192", new Character((char)192));
		EntityMap.put("Aacute", new Character((char)193));
		EntityMap.put("#193", new Character((char)193));
		EntityMap.put("Acirc", new Character((char)194));
		EntityMap.put("#194", new Character((char)194));
		EntityMap.put("Atilde", new Character((char)195));
		EntityMap.put("#195", new Character((char)195));
		EntityMap.put("Auml", new Character((char)196));
		EntityMap.put("#196", new Character((char)196));
		EntityMap.put("Aring", new Character((char)197));
		EntityMap.put("#197", new Character((char)197));
		EntityMap.put("AElig", new Character((char)198));
		EntityMap.put("#198", new Character((char)198));
		EntityMap.put("Ccedil", new Character((char)199));
		EntityMap.put("#199", new Character((char)199));
		EntityMap.put("Egrave", new Character((char)200));
		EntityMap.put("#200", new Character((char)200));
		EntityMap.put("Eacute", new Character((char)201));
		EntityMap.put("#201", new Character((char)201));
		EntityMap.put("Ecirc", new Character((char)202));
		EntityMap.put("#202", new Character((char)202));
		EntityMap.put("Euml", new Character((char)203));
		EntityMap.put("#203", new Character((char)203));
		EntityMap.put("Igrave", new Character((char)204));
		EntityMap.put("#204", new Character((char)204));
		EntityMap.put("Iacute", new Character((char)205));
		EntityMap.put("#205", new Character((char)205));
		EntityMap.put("Icirc", new Character((char)206));
		EntityMap.put("#206", new Character((char)206));
		EntityMap.put("Iuml", new Character((char)207));
		EntityMap.put("#207", new Character((char)207));
		EntityMap.put("ETH", new Character((char)208));
		EntityMap.put("#208", new Character((char)208));
		EntityMap.put("Ntilde", new Character((char)209));
		EntityMap.put("#209", new Character((char)209));
		EntityMap.put("Ograve", new Character((char)210));
		EntityMap.put("#210", new Character((char)210));
		EntityMap.put("Oacute", new Character((char)211));
		EntityMap.put("#211", new Character((char)211));
		EntityMap.put("Ocirc", new Character((char)212));
		EntityMap.put("#212", new Character((char)212));
		EntityMap.put("Otilde", new Character((char)213));
		EntityMap.put("#213", new Character((char)213));
		EntityMap.put("Ouml", new Character((char)214));
		EntityMap.put("#214", new Character((char)214));
		EntityMap.put("times", new Character((char)215));
		EntityMap.put("#215", new Character((char)215));
		EntityMap.put("Oslash", new Character((char)216));
		EntityMap.put("#216", new Character((char)216));
		EntityMap.put("Ugrave", new Character((char)217));
		EntityMap.put("#217", new Character((char)217));
		EntityMap.put("Uacute", new Character((char)218));
		EntityMap.put("#218", new Character((char)218));
		EntityMap.put("Ucirc", new Character((char)219));
		EntityMap.put("#219", new Character((char)219));
		EntityMap.put("Uuml", new Character((char)220));
		EntityMap.put("#220", new Character((char)220));
		EntityMap.put("Yacute", new Character((char)221));
		EntityMap.put("#221", new Character((char)221));
		EntityMap.put("THORN", new Character((char)222));
		EntityMap.put("#222", new Character((char)222));
		EntityMap.put("szlig", new Character((char)223));
		EntityMap.put("#223", new Character((char)223));
		EntityMap.put("agrave", new Character((char)224));
		EntityMap.put("#224", new Character((char)224));
		EntityMap.put("aacute", new Character((char)225));
		EntityMap.put("#225", new Character((char)225));
		EntityMap.put("acirc", new Character((char)226));
		EntityMap.put("#226", new Character((char)226));
		EntityMap.put("atilde", new Character((char)227));
		EntityMap.put("#227", new Character((char)227));
		EntityMap.put("auml", new Character((char)228));
		EntityMap.put("#228", new Character((char)228));
		EntityMap.put("aring", new Character((char)229));
		EntityMap.put("#229", new Character((char)229));
		EntityMap.put("aelig", new Character((char)230));
		EntityMap.put("#230", new Character((char)230));
		EntityMap.put("ccedil", new Character((char)231));
		EntityMap.put("#231", new Character((char)231));
		EntityMap.put("egrave", new Character((char)232));
		EntityMap.put("#232", new Character((char)232));
		EntityMap.put("eacute", new Character((char)233));
		EntityMap.put("#233", new Character((char)233));
		EntityMap.put("ecirc", new Character((char)234));
		EntityMap.put("#234", new Character((char)234));
		EntityMap.put("euml", new Character((char)235));
		EntityMap.put("#235", new Character((char)235));
		EntityMap.put("igrave", new Character((char)236));
		EntityMap.put("#236", new Character((char)236));
		EntityMap.put("iacute", new Character((char)237));
		EntityMap.put("#237", new Character((char)237));
		EntityMap.put("icirc", new Character((char)238));
		EntityMap.put("#238", new Character((char)238));
		EntityMap.put("iuml", new Character((char)239));
		EntityMap.put("#239", new Character((char)239));
		EntityMap.put("eth", new Character((char)240));
		EntityMap.put("#240", new Character((char)240));
		EntityMap.put("ntilde", new Character((char)241));
		EntityMap.put("#241", new Character((char)241));
		EntityMap.put("ograve", new Character((char)242));
		EntityMap.put("#242", new Character((char)242));
		EntityMap.put("oacute", new Character((char)243));
		EntityMap.put("#243", new Character((char)243));
		EntityMap.put("ocirc", new Character((char)244));
		EntityMap.put("#244", new Character((char)244));
		EntityMap.put("otilde", new Character((char)245));
		EntityMap.put("#245", new Character((char)245));
		EntityMap.put("ouml", new Character((char)246));
		EntityMap.put("#246", new Character((char)246));
		EntityMap.put("divide", new Character((char)247));
		EntityMap.put("#247", new Character((char)247));
		EntityMap.put("oslash", new Character((char)248));
		EntityMap.put("#248", new Character((char)248));
		EntityMap.put("ugrave", new Character((char)249));
		EntityMap.put("#249", new Character((char)249));
		EntityMap.put("uacute", new Character((char)250));
		EntityMap.put("#250", new Character((char)250));
		EntityMap.put("ucirc", new Character((char)251));
		EntityMap.put("#251", new Character((char)251));
		EntityMap.put("uuml", new Character((char)252));
		EntityMap.put("#252", new Character((char)252));
		EntityMap.put("yacute", new Character((char)253));
		EntityMap.put("#253", new Character((char)253));
		EntityMap.put("thorn", new Character((char)254));
		EntityMap.put("#254", new Character((char)254));
		EntityMap.put("yuml", new Character((char)255));
		EntityMap.put("#255", new Character((char)255));
		EntityMap.put("fnof", new Character((char)402));
		EntityMap.put("#402", new Character((char)402));
		EntityMap.put("Alpha", new Character((char)913));
		EntityMap.put("#913", new Character((char)913));
		EntityMap.put("Beta", new Character((char)914));
		EntityMap.put("#914", new Character((char)914));
		EntityMap.put("Gamma", new Character((char)915));
		EntityMap.put("#915", new Character((char)915));
		EntityMap.put("Delta", new Character((char)916));
		EntityMap.put("#916", new Character((char)916));
		EntityMap.put("Epsilon", new Character((char)917));
		EntityMap.put("#917", new Character((char)917));
		EntityMap.put("Zeta", new Character((char)918));
		EntityMap.put("#918", new Character((char)918));
		EntityMap.put("Eta", new Character((char)919));
		EntityMap.put("#919", new Character((char)919));
		EntityMap.put("Theta", new Character((char)920));
		EntityMap.put("#920", new Character((char)920));
		EntityMap.put("Iota", new Character((char)921));
		EntityMap.put("#921", new Character((char)921));
		EntityMap.put("Kappa", new Character((char)922));
		EntityMap.put("#922", new Character((char)922));
		EntityMap.put("Lambda", new Character((char)923));
		EntityMap.put("#923", new Character((char)923));
		EntityMap.put("Mu", new Character((char)924));
		EntityMap.put("#924", new Character((char)924));
		EntityMap.put("Nu", new Character((char)925));
		EntityMap.put("#925", new Character((char)925));
		EntityMap.put("Xi", new Character((char)926));
		EntityMap.put("#926", new Character((char)926));
		EntityMap.put("Omicron", new Character((char)927));
		EntityMap.put("#927", new Character((char)927));
		EntityMap.put("Pi", new Character((char)928));
		EntityMap.put("#928", new Character((char)928));
		EntityMap.put("Rho", new Character((char)929));
		EntityMap.put("#929", new Character((char)929));
		EntityMap.put("Sigma", new Character((char)931));
		EntityMap.put("#931", new Character((char)931));
		EntityMap.put("Tau", new Character((char)932));
		EntityMap.put("#932", new Character((char)932));
		EntityMap.put("Upsilon", new Character((char)933));
		EntityMap.put("#933", new Character((char)933));
		EntityMap.put("Phi", new Character((char)934));
		EntityMap.put("#934", new Character((char)934));
		EntityMap.put("Chi", new Character((char)935));
		EntityMap.put("#935", new Character((char)935));
		EntityMap.put("Psi", new Character((char)936));
		EntityMap.put("#936", new Character((char)936));
		EntityMap.put("Omega", new Character((char)937));
		EntityMap.put("#937", new Character((char)937));
		EntityMap.put("alpha", new Character((char)945));
		EntityMap.put("#945", new Character((char)945));
		EntityMap.put("beta", new Character((char)946));
		EntityMap.put("#946", new Character((char)946));
		EntityMap.put("gamma", new Character((char)947));
		EntityMap.put("#947", new Character((char)947));
		EntityMap.put("delta", new Character((char)948));
		EntityMap.put("#948", new Character((char)948));
		EntityMap.put("epsilon", new Character((char)949));
		EntityMap.put("#949", new Character((char)949));
		EntityMap.put("zeta", new Character((char)950));
		EntityMap.put("#950", new Character((char)950));
		EntityMap.put("eta", new Character((char)951));
		EntityMap.put("#951", new Character((char)951));
		EntityMap.put("theta", new Character((char)952));
		EntityMap.put("#952", new Character((char)952));
		EntityMap.put("iota", new Character((char)953));
		EntityMap.put("#953", new Character((char)953));
		EntityMap.put("kappa", new Character((char)954));
		EntityMap.put("#954", new Character((char)954));
		EntityMap.put("lambda", new Character((char)955));
		EntityMap.put("#955", new Character((char)955));
		EntityMap.put("mu", new Character((char)956));
		EntityMap.put("#956", new Character((char)956));
		EntityMap.put("nu", new Character((char)957));
		EntityMap.put("#957", new Character((char)957));
		EntityMap.put("xi", new Character((char)958));
		EntityMap.put("#958", new Character((char)958));
		EntityMap.put("omicron", new Character((char)959));
		EntityMap.put("#959", new Character((char)959));
		EntityMap.put("pi", new Character((char)960));
		EntityMap.put("#960", new Character((char)960));
		EntityMap.put("rho", new Character((char)961));
		EntityMap.put("#961", new Character((char)961));
		EntityMap.put("sigmaf", new Character((char)962));
		EntityMap.put("#962", new Character((char)962));
		EntityMap.put("sigma", new Character((char)963));
		EntityMap.put("#963", new Character((char)963));
		EntityMap.put("tau", new Character((char)964));
		EntityMap.put("#964", new Character((char)964));
		EntityMap.put("upsilon", new Character((char)965));
		EntityMap.put("#965", new Character((char)965));
		EntityMap.put("phi", new Character((char)966));
		EntityMap.put("#966", new Character((char)966));
		EntityMap.put("chi", new Character((char)967));
		EntityMap.put("#967", new Character((char)967));
		EntityMap.put("psi", new Character((char)968));
		EntityMap.put("#968", new Character((char)968));
		EntityMap.put("omega", new Character((char)969));
		EntityMap.put("#969", new Character((char)969));
		EntityMap.put("thetasym", new Character((char)977));
		EntityMap.put("#977", new Character((char)977));
		EntityMap.put("upsih", new Character((char)978));
		EntityMap.put("#978", new Character((char)978));
		EntityMap.put("piv", new Character((char)982));
		EntityMap.put("#982", new Character((char)982));
		EntityMap.put("bull", new Character((char)8226));
		EntityMap.put("#8226", new Character((char)8226));
		EntityMap.put("hellip", new Character((char)8230));
		EntityMap.put("#8230", new Character((char)8230));
		EntityMap.put("prime", new Character((char)8242));
		EntityMap.put("#8242", new Character((char)8242));
		EntityMap.put("Prime", new Character((char)8243));
		EntityMap.put("#8243", new Character((char)8243));
		EntityMap.put("oline", new Character((char)8254));
		EntityMap.put("#8254", new Character((char)8254));
		EntityMap.put("frasl", new Character((char)8260));
		EntityMap.put("#8260", new Character((char)8260));
		EntityMap.put("weierp", new Character((char)8472));
		EntityMap.put("#8472", new Character((char)8472));
		EntityMap.put("image", new Character((char)8465));
		EntityMap.put("#8465", new Character((char)8465));
		EntityMap.put("real", new Character((char)8476));
		EntityMap.put("#8476", new Character((char)8476));
		EntityMap.put("trade", new Character((char)8482));
		EntityMap.put("#8482", new Character((char)8482));
		EntityMap.put("alefsym", new Character((char)8501));
		EntityMap.put("#8501", new Character((char)8501));
		EntityMap.put("larr", new Character((char)8592));
		EntityMap.put("#8592", new Character((char)8592));
		EntityMap.put("uarr", new Character((char)8593));
		EntityMap.put("#8593", new Character((char)8593));
		EntityMap.put("rarr", new Character((char)8594));
		EntityMap.put("#8594", new Character((char)8594));
		EntityMap.put("darr", new Character((char)8595));
		EntityMap.put("#8595", new Character((char)8595));
		EntityMap.put("harr", new Character((char)8596));
		EntityMap.put("#8596", new Character((char)8596));
		EntityMap.put("crarr", new Character((char)8629));
		EntityMap.put("#8629", new Character((char)8629));
		EntityMap.put("lArr", new Character((char)8656));
		EntityMap.put("#8656", new Character((char)8656));
		EntityMap.put("uArr", new Character((char)8657));
		EntityMap.put("#8657", new Character((char)8657));
		EntityMap.put("rArr", new Character((char)8658));
		EntityMap.put("#8658", new Character((char)8658));
		EntityMap.put("dArr", new Character((char)8659));
		EntityMap.put("#8659", new Character((char)8659));
		EntityMap.put("hArr", new Character((char)8660));
		EntityMap.put("#8660", new Character((char)8660));
		EntityMap.put("forall", new Character((char)8704));
		EntityMap.put("#8704", new Character((char)8704));
		EntityMap.put("part", new Character((char)8706));
		EntityMap.put("#8706", new Character((char)8706));
		EntityMap.put("exist", new Character((char)8707));
		EntityMap.put("#8707", new Character((char)8707));
		EntityMap.put("empty", new Character((char)8709));
		EntityMap.put("#8709", new Character((char)8709));
		EntityMap.put("nabla", new Character((char)8711));
		EntityMap.put("#8711", new Character((char)8711));
		EntityMap.put("isin", new Character((char)8712));
		EntityMap.put("#8712", new Character((char)8712));
		EntityMap.put("notin", new Character((char)8713));
		EntityMap.put("#8713", new Character((char)8713));
		EntityMap.put("ni", new Character((char)8715));
		EntityMap.put("#8715", new Character((char)8715));
		EntityMap.put("prod", new Character((char)8719));
		EntityMap.put("#8719", new Character((char)8719));
		EntityMap.put("sum", new Character((char)8721));
		EntityMap.put("#8721", new Character((char)8721));
		EntityMap.put("minus", new Character((char)8722));
		EntityMap.put("#8722", new Character((char)8722));
		EntityMap.put("lowast", new Character((char)8727));
		EntityMap.put("#8727", new Character((char)8727));
		EntityMap.put("radic", new Character((char)8730));
		EntityMap.put("#8730", new Character((char)8730));
		EntityMap.put("prop", new Character((char)8733));
		EntityMap.put("#8733", new Character((char)8733));
		EntityMap.put("infin", new Character((char)8734));
		EntityMap.put("#8734", new Character((char)8734));
		EntityMap.put("ang", new Character((char)8736));
		EntityMap.put("#8736", new Character((char)8736));
		EntityMap.put("and", new Character((char)8743));
		EntityMap.put("#8743", new Character((char)8743));
		EntityMap.put("or", new Character((char)8744));
		EntityMap.put("#8744", new Character((char)8744));
		EntityMap.put("cap", new Character((char)8745));
		EntityMap.put("#8745", new Character((char)8745));
		EntityMap.put("cup", new Character((char)8746));
		EntityMap.put("#8746", new Character((char)8746));
		EntityMap.put("int", new Character((char)8747));
		EntityMap.put("#8747", new Character((char)8747));
		EntityMap.put("there4", new Character((char)8756));
		EntityMap.put("#8756", new Character((char)8756));
		EntityMap.put("sim", new Character((char)8764));
		EntityMap.put("#8764", new Character((char)8764));
		EntityMap.put("cong", new Character((char)8773));
		EntityMap.put("#8773", new Character((char)8773));
		EntityMap.put("asymp", new Character((char)8776));
		EntityMap.put("#8776", new Character((char)8776));
		EntityMap.put("ne", new Character((char)8800));
		EntityMap.put("#8800", new Character((char)8800));
		EntityMap.put("equiv", new Character((char)8801));
		EntityMap.put("#8801", new Character((char)8801));
		EntityMap.put("le", new Character((char)8804));
		EntityMap.put("#8804", new Character((char)8804));
		EntityMap.put("ge", new Character((char)8805));
		EntityMap.put("#8805", new Character((char)8805));
		EntityMap.put("sub", new Character((char)8834));
		EntityMap.put("#8834", new Character((char)8834));
		EntityMap.put("sup", new Character((char)8835));
		EntityMap.put("#8835", new Character((char)8835));
		EntityMap.put("nsub", new Character((char)8836));
		EntityMap.put("#8836", new Character((char)8836));
		EntityMap.put("sube", new Character((char)8838));
		EntityMap.put("#8838", new Character((char)8838));
		EntityMap.put("supe", new Character((char)8839));
		EntityMap.put("#8839", new Character((char)8839));
		EntityMap.put("oplus", new Character((char)8853));
		EntityMap.put("#8853", new Character((char)8853));
		EntityMap.put("otimes", new Character((char)8855));
		EntityMap.put("#8855", new Character((char)8855));
		EntityMap.put("perp", new Character((char)8869));
		EntityMap.put("#8869", new Character((char)8869));
		EntityMap.put("sdot", new Character((char)8901));
		EntityMap.put("#8901", new Character((char)8901));
		EntityMap.put("lceil", new Character((char)8968));
		EntityMap.put("#8968", new Character((char)8968));
		EntityMap.put("rceil", new Character((char)8969));
		EntityMap.put("#8969", new Character((char)8969));
		EntityMap.put("lfloor", new Character((char)8970));
		EntityMap.put("#8970", new Character((char)8970));
		EntityMap.put("rfloor", new Character((char)8971));
		EntityMap.put("#8971", new Character((char)8971));
		EntityMap.put("lang", new Character((char)9001));
		EntityMap.put("#9001", new Character((char)9001));
		EntityMap.put("rang", new Character((char)9002));
		EntityMap.put("#9002", new Character((char)9002));
		EntityMap.put("loz", new Character((char)9674));
		EntityMap.put("#9674", new Character((char)9674));
		EntityMap.put("spades", new Character((char)9824));
		EntityMap.put("#9824", new Character((char)9824));
		EntityMap.put("clubs", new Character((char)9827));
		EntityMap.put("#9827", new Character((char)9827));
		EntityMap.put("hearts", new Character((char)9829));
		EntityMap.put("#9829", new Character((char)9829));
		EntityMap.put("diams", new Character((char)9830));
		EntityMap.put("#9830", new Character((char)9830));
		EntityMap.put("quot", new Character((char)34));
		EntityMap.put("#34", new Character((char)34));
		EntityMap.put("amp", new Character((char)38));
		EntityMap.put("#38", new Character((char)38));
		EntityMap.put("lt", new Character((char)60));
		EntityMap.put("#60", new Character((char)60));
		EntityMap.put("gt", new Character((char)62));
		EntityMap.put("#62", new Character((char)62));
		EntityMap.put("OElig", new Character((char)338));
		EntityMap.put("#338", new Character((char)338));
		EntityMap.put("oelig", new Character((char)339));
		EntityMap.put("#339", new Character((char)339));
		EntityMap.put("Scaron", new Character((char)352));
		EntityMap.put("#352", new Character((char)352));
		EntityMap.put("scaron", new Character((char)353));
		EntityMap.put("#353", new Character((char)353));
		EntityMap.put("Yuml", new Character((char)376));
		EntityMap.put("#376", new Character((char)376));
		EntityMap.put("circ", new Character((char)710));
		EntityMap.put("#710", new Character((char)710));
		EntityMap.put("tilde", new Character((char)732));
		EntityMap.put("#732", new Character((char)732));
		EntityMap.put("ensp", new Character((char)8194));
		EntityMap.put("#8194", new Character((char)8194));
		EntityMap.put("emsp", new Character((char)8195));
		EntityMap.put("#8195", new Character((char)8195));
		EntityMap.put("thinsp", new Character((char)8201));
		EntityMap.put("#8201", new Character((char)8201));
		EntityMap.put("zwnj", new Character((char)8204));
		EntityMap.put("#8204", new Character((char)8204));
		EntityMap.put("zwj", new Character((char)8205));
		EntityMap.put("#8205", new Character((char)8205));
		EntityMap.put("lrm", new Character((char)8206));
		EntityMap.put("#8206", new Character((char)8206));
		EntityMap.put("rlm", new Character((char)8207));
		EntityMap.put("#8207", new Character((char)8207));
		EntityMap.put("ndash", new Character((char)8211));
		EntityMap.put("#8211", new Character((char)8211));
		EntityMap.put("mdash", new Character((char)8212));
		EntityMap.put("#8212", new Character((char)8212));
		EntityMap.put("lsquo", new Character((char)8216));
		EntityMap.put("#8216", new Character((char)8216));
		EntityMap.put("rsquo", new Character((char)8217));
		EntityMap.put("#8217", new Character((char)8217));
		EntityMap.put("sbquo", new Character((char)8218));
		EntityMap.put("#8218", new Character((char)8218));
		EntityMap.put("ldquo", new Character((char)8220));
		EntityMap.put("#8220", new Character((char)8220));
		EntityMap.put("rdquo", new Character((char)8221));
		EntityMap.put("#8221", new Character((char)8221));
		EntityMap.put("bdquo", new Character((char)8222));
		EntityMap.put("#8222", new Character((char)8222));
		EntityMap.put("dagger", new Character((char)8224));
		EntityMap.put("#8224", new Character((char)8224));
		EntityMap.put("Dagger", new Character((char)8225));
		EntityMap.put("#8225", new Character((char)8225));
		EntityMap.put("permil", new Character((char)8240));
		EntityMap.put("#8240", new Character((char)8240));
		EntityMap.put("lsaquo", new Character((char)8249));
		EntityMap.put("#8249", new Character((char)8249));
		EntityMap.put("rsaquo", new Character((char)8250));
		EntityMap.put("#8250", new Character((char)8250));
		EntityMap.put("euro", new Character((char)8364));
		EntityMap.put("#8364", new Character((char)8364));
	}

	ScraperModule() {}

	/**
	 * Get the contents, with caching, of the given URL, using the default timeout.
	 * @param url The URL to get the contents of.
	 * @throws java.io.IOException
	 * @return The contents of the URL provided.
	 */
	public String getContentsCached(URL url) throws IOException {
		return getContentsCached(url, GetContentsCached.DEFAULT_TIMEOUT);
	}

	/**
	 * Get the contents, with caching, of the given URL.
	 * @param url The URL to get the contents of.
	 * @param timeout The timeout to use when performing the operation.
	 * @throws java.io.IOException 
	 * @return The contents of the URL provided.
	 */
	public String getContentsCached(URL url, long timeout) throws IOException {
		GetContentsCached gcc=sites.get(url);
		if (gcc==null) {
			gcc=new GetContentsCached(url, timeout);
			sites.put(url, gcc);
		} else {
			if (gcc.getTimeout()>timeout)
				gcc.setTimeout(timeout);
		}

		synchronized (sites) {
			Iterator<Map.Entry<URL, GetContentsCached>> i = sites.entrySet().iterator();
			while (i.hasNext()) {
				if (i.next().getValue().expired()) {
					i.remove();
				}
			}
		}

		return gcc.getContents();
	}

	/**
	 * Get the regular expression Matcher object for a specific URL and regular expression.
	 * @param url The URL to perform the regular expression on.
	 * @param timeout The timeout to use when performing this operation.
	 * @param regex The regular expression to use.
	 * @throws java.io.IOException 
	 * @return The Matcher object requested.
	 */
	public Matcher getMatcher(URL url, long timeout, String regex) throws IOException {
		return getMatcher(url, timeout, Pattern.compile(regex));
	}

	/**
	 * Get the regular expression Matcher object for a specific URL and regular expression.
	 * @param url The URL to perform the regular expression on.
	 * @param regex The regular expression to use.
	 * @throws java.io.IOException 
	 * @return The Matcher object requested.
	 */
	public Matcher getMatcher(URL url, String regex) throws IOException {
		return getMatcher(url, Pattern.compile(regex));
	}

	/**
	 * Get the regular expression Matcher object for a specific URL and regular expression.
	 * @param url The URL to perform the regular expression on.
	 * @param regex The Pattern object containing the regular expression to use.
	 * @throws java.io.IOException 
	 * @return The Matcher object requested.
	 */
	public Matcher getMatcher(URL url, Pattern regex) throws IOException {
		return regex.matcher(getContentsCached(url));
	}

	/**
	 * Get the regular expression Matcher object for a specific URL and regular expression.
	 * @param url The URL to perform the regular expression on.
	 * @param timeout The timeout to use when performing this operation.
	 * @param regex The Pattern object containing the regular expression to use.
	 * @throws java.io.IOException 
	 * @return The Matcher object requested.
	 */
	public Matcher getMatcher(URL url, long timeout, Pattern regex) throws IOException {
		return regex.matcher(getContentsCached(url, timeout));
	}

	/**
	 * Replace HTML special character codes with the actual character.
	 * @param html The String to perform the replacement upon.
	 * @return The String after replacement has been performed.
	 */
	public String convertEntities(final String html) {
		// Sorry, I just couldn't bring myself to do foreach (hashmap => key, val) html=html.replaceall(key, val);

		StringBuilder buf = new StringBuilder();
		int l=html.length();
		int p=0;
		int lastp=0;
		while (p<l)	{
			p=html.indexOf("&", p);

			if (p==-1) {
				break;
			}

			int semi=html.indexOf(";",p);

			if (semi==-1) {
				break;
			}

			Character c=EntityMap.get(html.substring(p+1,semi));

			if (c==null) {
				buf.append(html.substring(lastp, semi+1));
			} else {
				buf.append(html.substring(lastp,p)).append(c);
			}

			p=semi+1;
			lastp=p;
		}

		buf.append(html.substring(lastp));
		return buf.toString();
	}

	/**
	 * Replace bold HTML tags with the IRC equivalent.
	 * @param html The HTML String to perform the replacement on.
	 * @return The String suitably formatted to perform bold on IRC.
	 */
	public String boldTags(String html) {
		return html.replaceAll("(?i)<b>", Colors.BOLD).replaceAll("(?i)</b>", Colors.NORMAL);
	}
	
	public String quoteURLs(String html) {
		//return html.replaceAll("<a +?href *= *\"(.*?)\" *?>","[$1] ").replaceAll("</a>","");
		return html;
		//This is going to require more than: return html.replaceAll("<a +?href *= *\"(.*?)\" *?>",Colors.REVERSE + "<$1> " + Colors.NORMAL).replaceAll("</a>","");
	}

	/**
	 * Remove all tags from a String of HTML.
	 * @param html The String to filter.
	 * @return The String, minus any HTML tags.
	 */
	public String stripTags(String html) {
		return html.replaceAll("<.*?>","");
	}

	/**
	 * Tidy up a string of HTML, removing newlines and HTML special characters.
	 * @param html The HTML to tidy.
	 * @return The tidy HTML.
	 */
	public String cleanup(String html) {
		return convertEntities(html.trim().replaceAll("\n",""));
	}

	/**
	 * Escapes quotes in a String.
	 * @param html The String to clean
	 * @return The clean String, with escaped quotes.
	 */
	public String escapeQuotes(String html) {
		return html.replaceAll("'","\\'").replaceAll("\"", "\\\"");
	}

	/**
	 * Cleans a Sting of HTML of all tags in order to make it suitable for output in IRC.
	 * @param html The String containing HTML tags that is to be prepared for IRC output.
	 * @return A string ready for output into IRC.
	 */
	public String readyForIrc(String html) {
		return stripTags(quoteURLs(boldTags(cleanup(html))));
	}

	/**
	 * Cleans a String of HTML in order to make it suitable for output in a HTML page.
	 * @param html The String containing HTML tags that is to be prepared for HTML output.
	 * @return A string ready for output into HTML.
	 */
	public String readyForHtml(String html) {
		return escapeQuotes(stripTags(quoteURLs(boldTags(cleanup(html)))));
	}
	
	/**
	 *
	 */
	public String escapeForHTML(String html) {
		return html.replaceAll("&", "&amp;").replaceAll("<", "&lt;").replaceAll(">", "&gt;").replaceAll("'", "&#27;").replaceAll("\"", "&quot;");
	}
}

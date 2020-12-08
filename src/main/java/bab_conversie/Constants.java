package bab_conversie;
public class Constants {
	
	// marker to be user as marker of an empty line, which is normally the edge of a paragraph
	static String EMPTY_LINE_MARKER = "EMPTY_LINE";
	
	// known abbreviations
	// we need to know the abbreviations, because those mustn't be separated from their dots
	// during tokenization, in contrary to other common words
	static String[] KNOWN_ABBREVIATIONS = new String[]{
		// classic abbreviations
		
		"Aa.", "Ab.", "Ab:", "Abr:", "Achtb.", "Adt.", "Adr:", "aenmr.", "aen:", "Agtb:", "Ambr.", 
		"amc.", "Amst.", "and:", "Ands.", "antw:", "Anno:", "ano.", "Ano.", "Ao:", "Ao.", "ao.", "AP:", "aps.",
		"Atr.", "Aug:", "Aug.", "augs:", "Augs:", "Augt:", 
		"beantw:", "Beck.", "bed.", "bed:j", "bem:", "bet.", "Br.", "Br:", "br.", "Bs:", "burg.",
		"Cap.", "Cap:", "Capit.", "Capls:", "Capn.", "Cap:n", "Cap.n", "capn.", "Capn:", "Capne.",
		"capp.", "capp:", "capt:", "capt.", "Capt.", "Capt:", "capt::", "Captn.", "Captn:", "captn.",
		"Carag.", "cent.", "Ch.", "Ch:", "Chr.",
		"Co:", "codro.", "Coll:", "Coll.", "comm:",
		"Comp.", "Comp:", "Compa:", "compa:", "Compe:", "Compl:", "conr.", "conso:", "Coopv.", 
		"Corn.", "Corn:", "Corns.", "Cour:", "Crull:",
		"Dani:ge", "dank:", "dato:", "Dd:", "DDG.", "De:", "Dec:", "Dec.", "Decb:", "Decbr.", "deeHr.",
		"Den.", "den.", "den:", "D:G", "Dg.g.", "DGG.", "DGG:", "DGGL:",
		"DeHr.", "dH:", "DH:", "dHeer.", "DHeer:", "dHeer:", "DHeer.", 
		"d'hr.", "d’hr:", "DHr.", "Dhr:", "Dhr.", "dhr.", "dhr:", 
		"dm.", "dn:", "Do.", "do.", "doct.", "doo:", "Dr:", "dr.",
		"dupp.", "Duyet:", "duyt:", "DW:", "dw.", "DWD:", "DWDr:", "dwdw:",
		"Ed.", "Ed:", "ED:", "ed:", "Edl.", "Edle.", "Eds.", "EdL.", "Edl:", "Eds:",  
		"Eer:", "eerw:", "Eerw.", "Eerw:", "Eerws:", "Ehr:", "El:", "El:El:", 
		"end:","ende:", "ende.", "Eng.", "Eng:", 
		"Engl:", "enz:", "enz.",
		"erv.", "etc.", "Etc:", "etc:", "EU:", "Eust.", "Eusts:", "Everh.", "ew.", "Exell.",
		"fav.", "F:C:", "feb.", "Feb:", "feb:", "febr.", "febr:", "Febr:", "Febr.", "Febrc.", "Febru:",
		"ferct:", "flo.", "fr.", "Fre:", "Fred:", "Fredk.",
		"gb.", "Gd.", "GD:", "Ge.", "geaff:", "Geb.", "geb:", "Geb:", "Gebo:", 
		"gecr.", "ged:", "Gee:", "geg.:",
		"gem:", "gem.", "gemd:", "gem.de", "Gemss:", "gen.", "gen:", "Gener:", 
		"ges:", "gesn.", "Gest:", "gestr:", "Gestr:", 
		"getr.", "getr:",
		"gf.", "ghijl.", "ghij.l.", "gl.", "gl:", 
		"Gl:", "gln.", "gr:", "Gt.", "Gt:", "gt:", "guld:", "Guld:", 
		"h:", "Haasz.", "Hans.", "HEd:", "Heer:", "heer.", "Hend:", "Hendk.", "Hendr:", "hg.", "Hl.", 
		"holl.", "Holl:", "holls:", "Holls.", "Hollt.", "hollt.", 
		"Hol.g.gulds", "Hoogl.", "hoogl.", "Hoogw:", "hoogw:", "Hopp.", "houw.",
		"Hr:", "Hr.", "hr.", "hr:", "Hre.", "Hren.", "Hs.", "HS.", "hunEd:",
		"ii.", "il:",
		"Jacob:", "jan.", "Jan.", "jan:", "Jan:", "janew:", "Janij.", "Jann:", "Jann.", 
		"janss.", "Jansz.", "jansz.", "Janu.", "janu:", "Janu:", "Janw:", "Jb.", "J:G:", "Jn.",
		"Joh.", "Joh:", "Johs:", "Js.", "Juff.", "Juff:", "Juffr.", "Juffw.", "juffw:", "Jur:", "Jür:",
		"kaa.", "komps.", "komps:", "koopm.",
		"l:", "lb.", "lb:", "Leff:", "ll.", "LL:", "Loff.", "loff:", "Lt.",
		"manqr:", "marg:", "Matth:", "Med.", "Meel:", "Mej.", 
		"mensg.", "merc.", "Met:", "met:", "meusz.", "mevr:", "Mevr:", "Mevr.",
		"miss:", "Miss:", "miss.", "Monr:", "Mons.", "mons.", 
		"Mons:", "Monsi.", "monsr.", "monsr:", "Monsr.", 
		"Monsr:", "Moog:", "Mord.", "morg:", "Mr:", "mr:", "Mr.", "mr.", "Ms.", "MynH:",
		"N:B:", "NB.", "NB:", "Neusd.", "NL:", 
		"No.", "no.", "nobr.", "nov.", "nov:", "Nov.", 
		"Nov:", "Novb:", "novb:", "Novbr.", "novbr:", "nove.", "Nove:", "Novr:", "NW:",
		"oblig.", "Oc.", "Oct.", "Octb:", "octbr:", "octbr.", "octob.", "octob:", "Octob:", "OI:", 
		"onderd:", "ondr:", "ond:r", "onferct:", "ontf:", "Ontf:", "ontg:", "oom:", "opd.", "ord:",
		"Pak:", "Pall:", "passt.", "pCapt:", "pCt.", "pe.", "pen:", "penn:", "perc:", "Phils:", "PJ.", "Pl:",
		"Pmo:", "pp.stc:", "Pr.", "pr.", "pr:", "Pr:", "Praec:", "pr:ai:", "proc:",
		"PS.", "PS:", "Ps:", "P:S:", "Ps.", "ps.", "P:V:", 
		"Raaps.", "rds:", "Reck.", "Reckt.", "reeck.", "reek:", "Reek.", 
		"Reeke.", "reekg:", "Reekg.", "rek.", "RG:", 
		"Risp.", "ross.", "Rout.", ":rs",
		"sall:", "salts.", "sch:",
		"schto.", "sec:", "Senor.", "Sepr:", "Sept.", "sept.", "sept:", "Sept:", "Septb:", "Sikte:",
		"SL.", "smorg.", "souw:", "Sr.", "sr.", "sr:", "Sr:",
		"Ss.", "SS.", "St.", "St:", "st.", "st:", "Ste.", "Stf.", "sts.", "stv:", 
		"sub:", "suck:", "stud:", "stud:n", "stuv.", "stuyv:",
		"Ta.", "Th.", "To.",
		"t:s:v:r:", 
		"EDL:", "ue:", "ue.", "Ue.", "uE.", "U.E.", "UE.", "UE:", "u.E.", "uE:", "UEd’:", "uEd.", "Ued.", "UEdl.", 
		"üEd:", "UEd:", "Ued:", "uEd:", "UEd.", "UED:", "ued:", "Uede.",
		"UEdg.", "UEdg:", "UEdG:", "UEDn.", "UEDns.", "UEdns.",
		"uEds:", "UEd:s", "uEds.", "UEds.", "UEds:", "Ueds.", "UEDW.",
		"UE.e", "UEl:", "UE.’t", "Ul.", "ul.", "u.l.", "UL.", "UL:", "Ul:", "ul:", "Ulto.", 
		"UUedl:", "Utr:",
		"Uw:", "UW:", "uwE.", "UWE.", "UWE:", "UWEd.", "uwEd:", "UwEd:", "UWed:", "UwEd.", "UWED:", "UWEd:", 
		"ůwEd:", "uwed.", "uwed:", "Uwed:", "UWed.", "UWEde:", "UWEDl:", "Uw.Ed.Gebo:",
		"UWEd.s", "UWEds:", "UWEds:", "uwel.", "Uwel.",
		"uwelE:", "UwelE:", "Uweled:", "uwelEd.", "uwelEdG.", "UwelEdG.", "uwent.",
		"v:", "vand:", "VE:", "ve:", "VE.", "VED:", "VEds.", "Vl.", "vl.", "v:l:", "V.L.", "v.l.", 
		"voorl.", "Voorn:", "vor.", "vrou.", "vrouw:",
		"Waerde:", "Wed.", "Wed:", "wed.", "Wedw.", "weduw.", "Weed:", "wees.", "WelEd:", "Wem.", 
		"Wel:", "welEd.", "Weled:", "weled:", "wijse:", "Will.", "Will:", "willm:", "Wilm:", "Wm.", "Wm:",
		"xb:", "xder:", "Yland:",
		"Zal.", "ZEd:", "Zr.", "zr.", "ZZO:", "zynE:", "zynE.", "zynEd.", "ZynEd."
	};

}

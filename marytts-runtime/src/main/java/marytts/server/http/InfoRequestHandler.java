/**
 * Copyright 2007 DFKI GmbH.
 * All Rights Reserved.  Use is subject to license terms.
 *
 * This file is part of MARY TTS.
 *
 * MARY TTS is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, version 3 of the License.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 */
package marytts.server.http;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.Locale;
import java.util.Map;
import java.util.Scanner;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;
import java.util.ArrayList;
import java.io.FileReader;
import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;

import marytts.features.FeatureProcessorManager;
import marytts.features.FeatureRegistry;
import marytts.modules.synthesis.Voice;
import marytts.util.MaryRuntimeUtils;
import marytts.util.MaryUtils;
import marytts.util.http.Address;

import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.nio.entity.NStringEntity;

/**
 * Processor class for information http requests to Mary server
 * 
 * @author Oytun T&uuml;rk, Marc Schr&ouml;der
 */
public class InfoRequestHandler extends BaseHttpRequestHandler {

    private SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss z");
    private String startedAt;
    private String versionInfo;
    
	public InfoRequestHandler() {
		super();
		dateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
		startedAt = dateFormat.format(new Date());
	}

	@Override
	protected void handleClientRequest(String absPath, Map<String, String> queryItems, HttpResponse response,
			Address serverAddressAtClient) throws IOException {
		// Individual info request
		String infoResponse = handleInfoRequest(absPath, queryItems, response);
		if (infoResponse == null) { // error condition, handleInfoRequest has set an error message
			return;
		}

		response.setStatusCode(HttpStatus.SC_OK);
		try {
			NStringEntity entity = new NStringEntity(infoResponse, "UTF-8");
			entity.setContentType("text/plain; charset=UTF-8");
			response.setEntity(entity);
		} catch (UnsupportedEncodingException e) {
		}
	}

     	// STTS addition, November 2017
     	private String getVersionInfo() throws Exception {
	    ArrayList<String> res = new ArrayList<String>();
	    String buildInfoFile = "/wikispeech/marytts/build_info.txt";
	    if (new File(buildInfoFile).exists()) {
		Scanner sc = new Scanner(new BufferedReader(new FileReader(buildInfoFile)));
		while (sc.hasNextLine()) {
		    String l = sc.nextLine().trim();
		    if (l.trim().length()>0) {
			res.add(l);
		    }
		}
		sc.close();
	    } else {
		logger.info("[InfoRequestHandler] No build info file found: " + buildInfoFile);
		System.err.println("[InfoRequestHandler] No build info file found: " + buildInfoFile);
		res.add("Application name: marytts");
		res.add("Build timestamp: n/a");
		res.add("Built by: user");
		try {
		    Process p = Runtime.getRuntime().exec("git describe --tags");
		    BufferedReader stdout = new BufferedReader(new InputStreamReader(p.getInputStream()));
		    BufferedReader stderr = new BufferedReader(new InputStreamReader(p.getErrorStream()));
		    Scanner sce = new Scanner(stderr).useDelimiter("\\Z");
		    if (sce.hasNext()) {
			String s = sce.next().trim();
			System.err.println("[InfoRequestHandler] Couldn't retrieve git relase info: " + s);
			logger.info("[InfoRequestHandler] Couldn't retrieve git release info: " + s);
			res.add("Release: unknown");
		    } else {
			Scanner sc = new Scanner(stdout).useDelimiter("\\Z");
			if (sc.hasNext()) {
			    String s = sc.next().trim();
			    res.add("Release: " + s);
			} else {
			    System.err.println("[InfoRequestHandler] Couldn't retrieve git release info: " + "??");
			    logger.info("[InfoRequestHandler] Couldn't retrieve git release info: " + "??");
			    res.add("Release: unknown");
			}
		    }
		} catch (Exception e) {
		    System.err.println("[InfoRequestHandler] Couldn't retrieve git release info: " + e.getMessage());
		    logger.info("[InfoRequestHandler] Couldn't retrieve git release info: " + e.getMessage());
		}
	    }

	    res.add("Started: " + startedAt);
	    String resString = "";
	    for (String s : res)
		resString += s + "\n";
	    return resString.trim();
     	}

     	private String handleInfoRequest(String absPath, Map<String, String> queryItems, HttpResponse response) {
		logger.debug("New info request: " + absPath);
		if (queryItems != null) {
			for (String key : queryItems.keySet()) {
				logger.debug("    " + key + "=" + queryItems.get(key));
			}
		}

		assert absPath.startsWith("/") : "Absolute path '" + absPath + "' does not start with a slash!";
		String request = absPath.substring(1); // without the initial slash

		if (request.equals("version")) {
		    if (this.versionInfo!= null) return this.versionInfo;
		    else {
			try {
			    this.versionInfo = getVersionInfo();
			    return this.versionInfo;
			} catch (Exception e) {
			    MaryHttpServerUtils.errorInternalServerError(response, "couldn't retrieve version info", e);
			    return null;
			}
		    }
		    //return MaryRuntimeUtils.getMaryVersion();
		}
		else if (request.equals("datatypes"))
			return MaryRuntimeUtils.getDataTypes();
		else if (request.equals("locales"))
			return MaryRuntimeUtils.getLocales();
		else if (request.equals("voices"))
			return MaryRuntimeUtils.getVoices();
		else if (request.equals("audioformats"))
			return MaryRuntimeUtils.getAudioFileFormatTypes();
		else if (request.equals("exampletext")) {
			if (queryItems != null) {
				// Voice example text
				String voice = queryItems.get("voice");
				if (voice != null) {
					return MaryRuntimeUtils.getVoiceExampleText(voice);
				}
				String datatype = queryItems.get("datatype");
				String locale = queryItems.get("locale");
				if (datatype != null && locale != null) {
					Locale loc = MaryUtils.string2locale(locale);
					return MaryRuntimeUtils.getExampleText(datatype, loc);
				}
			}
			MaryHttpServerUtils.errorMissingQueryParameter(response, "'datatype' and 'locale' or 'voice'");
			return null;
		} else if (request.equals("audioeffects"))
			return MaryRuntimeUtils.getDefaultAudioEffects();
		else if (request.equals("audioeffect-default-param")) {
			if (queryItems != null) {
				String effect = queryItems.get("effect");
				if (effect != null)
					return MaryRuntimeUtils.getAudioEffectDefaultParam(effect);
			}
			MaryHttpServerUtils.errorMissingQueryParameter(response, "'effect'");
			return null;
		} else if (request.equals("audioeffect-full")) {
			if (queryItems != null) {
				String effect = queryItems.get("effect");
				String params = queryItems.get("params");
				if (effect != null && params != null) {
					return MaryRuntimeUtils.getFullAudioEffect(effect, params);
				}
			}
			MaryHttpServerUtils.errorMissingQueryParameter(response, "'effect' and 'params'");
			return null;
		} else if (request.equals("audioeffect-help")) {
			if (queryItems != null) {
				String effect = queryItems.get("effect");
				if (effect != null) {
					return MaryRuntimeUtils.getAudioEffectHelpText(effect);
				}
			}
			MaryHttpServerUtils.errorMissingQueryParameter(response, "'effect'");
			return null;
		} else if (request.equals("audioeffect-is-hmm-effect")) {
			if (queryItems != null) {
				String effect = queryItems.get("effect");
				if (effect != null) {
					return MaryRuntimeUtils.isHmmAudioEffect(effect);
				}
			}
			MaryHttpServerUtils.errorMissingQueryParameter(response, "'effect'");
			return null;
		} else if (request.equals("features") || request.equals("features-discrete")) {
			if (queryItems != null) {
				// List of features that can be computed for the voice
				FeatureProcessorManager mgr = null;
				String voiceName = queryItems.get("voice");
				String localeName = queryItems.get("locale");
				if (voiceName != null) {
					Voice voice = Voice.getVoice(voiceName);
					if (voice == null) {
						MaryHttpServerUtils
								.errorWrongQueryParameterValue(response, "voice", voiceName, "No voice with that name");
						return null;
					}
					mgr = FeatureRegistry.getFeatureProcessorManager(voice);
					if (mgr == null) {
						mgr = FeatureRegistry.getFeatureProcessorManager(voice.getLocale());
					}
					if (mgr == null) {
						mgr = FeatureRegistry.getFeatureProcessorManager(new Locale(voice.getLocale().getLanguage()));
					}
					if (mgr == null) {
						mgr = FeatureRegistry.getFallbackFeatureProcessorManager();
					}
				} else if (localeName != null) {
					Locale locale = MaryUtils.string2locale(localeName);
					mgr = FeatureRegistry.getFeatureProcessorManager(locale);
					if (mgr == null) {
						mgr = FeatureRegistry.getFeatureProcessorManager(new Locale(locale.getLanguage()));
					}
					if (mgr == null) {
						StringBuilder localeList = new StringBuilder();
						for (Locale l : FeatureRegistry.getSupportedLocales()) {
							if (localeList.length() > 0)
								localeList.append(",");
							localeList.append(l.toString());
						}
						MaryHttpServerUtils.errorWrongQueryParameterValue(response, "locale", localeName,
								"The locale is not supported.<br />" + "Supported locales: <code>" + localeList + "</code>");
						return null;
					}
				}
				if (mgr != null)
					if (request.equals("features-discrete")) {
						String discreteFeatureNames = mgr.listByteValuedFeatureProcessorNames()
								+ mgr.listShortValuedFeatureProcessorNames();
						return discreteFeatureNames;
					}
				return mgr.listFeatureProcessorNames();
			}
			MaryHttpServerUtils.errorMissingQueryParameter(response, "'voice' or 'locale'");
			return null;
		} else if (request.equals("vocalizations")) {
			if (queryItems != null) {
				String voice = queryItems.get("voice");
				if (voice != null) {
					return MaryRuntimeUtils.getVocalizations(voice);
				}
			}
			MaryHttpServerUtils.errorMissingQueryParameter(response, "'voice'");
			return null;
		} else if (request.equals("styles")) {
			if (queryItems != null) {
				String voice = queryItems.get("voice");
				if (voice != null) {
					return MaryRuntimeUtils.getStyles(voice);
				}
			}
			MaryHttpServerUtils.errorMissingQueryParameter(response, "'voice'");
			return null;
		} else if (request.equals("ping")) {
		    return "marytts";
		}
		MaryHttpServerUtils.errorFileNotFound(response, request);
		return null;
	}

}

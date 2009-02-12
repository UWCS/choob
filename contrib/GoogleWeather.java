import java.io.IOException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Arrays;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElementRef;
import javax.xml.bind.annotation.XmlElementRefs;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.namespace.QName;
import javax.xml.transform.Source;
import javax.xml.ws.Dispatch;
import javax.xml.ws.Service;
import javax.xml.ws.handler.MessageContext;
import javax.xml.ws.http.HTTPBinding;

import uk.co.uwcs.choob.modules.Modules;
import uk.co.uwcs.choob.support.ChoobNoSuchCallException;
import uk.co.uwcs.choob.support.IRCInterface;
import uk.co.uwcs.choob.support.events.Message;

/**
 * Plugin for Querying weather information from google.
 * @author benji
 */
public class GoogleWeather
{
	private static final String baseURL = "http://www.google.com/ig/api?weather=";
	private static final Class[] DATA_TYPES = {GoogleMapsResponse.class,Weather.class,CurrentConditions.class,ForecastConditions.class,DataItem.class};

	private GoogleWeather()
	{

	}

	public String[] info()
	{
		return new String[] {
			"Plugin to get weather forecasts from http://www.google.com/ig",
			"The Choob Team",
			"choob@uwcs.co.uk",
			"0.2"
		};
	}

	private static final HashMap<String,String> symbols = new HashMap<String,String>()
	{
		private static final long serialVersionUID = 997592107556024573L;

	{
		put("Cloudy","☁");
		put("Rain","☂");
		put("Light Rain","☂");
	}};

	public <T> T getXmlFromHTTP(final String url, final Class... dataTypes) throws IOException, JAXBException
	{
		final QName qname = new QName("", "");
		final Service service = Service.create(qname);
		service.addPort(qname, HTTPBinding.HTTP_BINDING, baseURL);
		final Dispatch<Source> dispatcher =  service.createDispatch(qname, Source.class, Service.Mode.PAYLOAD);
		final Map<String, Object> requestContext = dispatcher.getRequestContext();
		requestContext.put(MessageContext.HTTP_REQUEST_METHOD, "GET");
		requestContext.put(MessageContext.HTTP_REQUEST_HEADERS,new HashMap<String,List<String>>(){{put("User-Agent",Arrays.asList("Opera/8.51 (X11; Linux x86_64; U; en)"));}});
		requestContext.put(Dispatch.ENDPOINT_ADDRESS_PROPERTY, url);
		final Source got = dispatcher.invoke(null);
		final JAXBContext context = JAXBContext.newInstance(dataTypes);
		final Unmarshaller u = context.createUnmarshaller();
		return (T)u.unmarshal(got);
	}

	private String getWeather(final String weatherLocation) throws IOException, JAXBException, LocationNotFoundException
	{
		final GoogleMapsResponse response = getXmlFromHTTP(baseURL + URLEncoder.encode(weatherLocation, "UTF-8"), DATA_TYPES);
		if (response == null || response.getWeather() == null || response.getWeather().getCurrentConditions() == null)
			throw new LocationNotFoundException();
		final String conditions = response.getWeather().getCurrentConditions().getCondition().getData();
		final String temp = response.getWeather().getCurrentConditions().getTemp_c().getData();
		final ForecastInformation inf = response.getWeather().getForecastInformation();
		final StringBuilder forecast = new StringBuilder();
		for (final ForecastConditions fCons : response.getWeather().getForeCastConditions())
			forecast
				.append(fCons.getDay_of_week().getData())
				.append(" ")
				.append(fCons.getCondition().getData())
				.append("; ");

		return "Weather in " +
			(inf != null ? inf.getCity() : weatherLocation ) +
			" is " +
			(symbols.containsKey(conditions) ? symbols.get(conditions) + " " : "") +
			conditions +
			". Current temperature is " +
			temp +
			"°C. " +
			(forecast.length() > 0 ? "Forecast is - " + forecast.toString() : "");
	}

	private Modules mods;
	private IRCInterface irc;

	public GoogleWeather(final Modules mods, final IRCInterface irc)
	{
		this.mods = mods;
		this.irc = irc;
	}

	public String[] optionsUser = { "Location" };
	public String[] optionsUserDefaults = { "coventry" };

	public String[] helpOptionGoogleWeatherLocation =
	{
		"Set your home location for the weather command with no parameters."
	};

	public boolean optionCheckUserWeather(final String value, final String nick)
	{
		return true; //hmm
	}

	private String checkOption(final String userNick)
	{
		try
		{
 			return (String)mods.plugin.callAPI("Options", "GetUserOption", userNick,"Location", optionsUserDefaults[0]);
		} catch (final ChoobNoSuchCallException e)
		{
			return optionsUserDefaults[0];
		}
	}

	public String[] helpCommandWeather =
	{
		"Return weather data for a location",
		"<LOCATION>",
		"<LOCATION> can be any of a text location eg: 'london' or a US zip code, or any other location google weather accepts"
	};
	public void commandWeather( final Message mes )
	{
		final List<String> params = mods.util.getParams(mes,2);
		String location = "";
		if (params.size()<2)
		{
			location = checkOption(mes.getNick());
		}
		if (location == null || location.equals(""))
			location = mods.util.getParams(mes, 1).get(1);

		if (location.equals(""))
		{
			irc.sendContextReply(mes, "Please give me a location to lookup.");
			return;
		}

		try
		{
			irc.sendContextReply(mes,getWeather(location));
		} catch (final IOException e)
		{
			System.err.println(e.getMessage());
			e.printStackTrace();
			irc.sendContextReply(mes,"Could not contact the site to obtain weather information");
		} catch (final JAXBException e)
		{
			System.err.println(e.getMessage());
			e.printStackTrace();
			irc.sendContextReply(mes,"Could not understand the weather information");
		} catch (final LocationNotFoundException e)
		{
			irc.sendContextReply(mes,"Could not retrieve any weather information for that location");
		} catch (final Throwable e)
		{
			Throwable ex = e;
			while (ex != null)
			{
				System.err.println(ex.getMessage());
				ex.printStackTrace();
				System.out.println("Cause is " + ex.getCause());
				ex = ex.getCause();
			}
		}
	}

	public static void main(final String[] args) throws IOException, JAXBException, LocationNotFoundException
	{
		if (args.length != 1)
		{
			System.err.println("Usage: <Location>");
			System.exit(-1);
		}
		final GoogleWeather m = new GoogleWeather();
		System.out.println(m.getWeather(args[0]));
	}
}

class LocationNotFoundException extends Exception {

	/**
	 *
	 */
	private static final long serialVersionUID = 3732524579011476793L;}

/**
 * Represents the response google maps will send us
 * @author benji
 */
@XmlRootElement(name = "xml_api_reply")
class GoogleMapsResponse
{

	private Weather weather;

	public void setWeather(final Weather weather)
	{
		this.weather = weather;
	}

	public Weather getWeather()
	{
		return weather;
	}
}


class Weather
{

	public CurrentConditions getCurrentConditions()
	{
		for (final Object o : getForeCastInformation())
		{
			if (o instanceof CurrentConditions)
			{
				return (CurrentConditions) o;
			}
		}
		return null;
	}

	public List<ForecastConditions> getForeCastConditions()
	{
		final ArrayList<ForecastConditions> forecastConditions = new ArrayList<ForecastConditions>();
		for (final Object o : getForeCastInformation())
		{
			if (o instanceof ForecastConditions)
			{
				forecastConditions.add((ForecastConditions) o);
			}
		}
		return forecastConditions;
	}

	public ForecastInformation getForecastInformation()
	{
		for (final Object o : getForeCastInformation())
		{
			if (o instanceof ForecastInformation)
			{
				return (ForecastInformation)o;
			}
		}
		return null;
	}

	@XmlElementRefs(
	{
		@XmlElementRef(name = "forecast_information", type = ForecastInformation.class),
		@XmlElementRef(name = "current_conditions", type = CurrentConditions.class),
		@XmlElementRef(name = "forecast_conditions", type = ForecastConditions.class)
	})
	protected List<Object> foreCastInformation;

	public List<Object> getForeCastInformation()
	{
		if (foreCastInformation == null)
		{
			foreCastInformation = new ArrayList<Object>();
		}
		return this.foreCastInformation;
	}
}

@XmlRootElement(name="forecast_conditions")
class ForecastConditions
{

	private DataItem day_of_week;
	private DataItem low;
	private DataItem high;
	private DataItem icon;
	private DataItem condition;

	public DataItem getDay_of_week()
	{
		return day_of_week;
	}

	public void setDay_of_week(final DataItem day_of_week)
	{
		this.day_of_week = day_of_week;
	}

	public DataItem getLow()
	{
		return low;
	}

	public void setLow(final DataItem low)
	{
		this.low = low;
	}

	public DataItem getHigh()
	{
		return high;
	}

	public void setHigh(final DataItem high)
	{
		this.high = high;
	}

	public DataItem getIcon()
	{
		return icon;
	}

	public void setIcon(final DataItem icon)
	{
		this.icon = icon;
	}

	public DataItem getCondition()
	{
		return condition;
	}

	public void setCondition(final DataItem condition)
	{
		this.condition = condition;
	}
}


@XmlRootElement(name="current_conditions")
class CurrentConditions
{

	private DataItem condition;
	private DataItem temp_f;
	private DataItem temp_c;
	private DataItem humidity;
	private DataItem icon;
	private DataItem wind_direction;

	public DataItem getCondition()
	{
		return condition;
	}

	public void setCondition(final DataItem condition)
	{
		this.condition = condition;
	}

	public DataItem getTemp_f()
	{
		return temp_f;
	}

	public void setTemp_f(final DataItem temp_f)
	{
		this.temp_f = temp_f;
	}

	public DataItem getTemp_c()
	{
		return temp_c;
	}

	public void setTemp_c(final DataItem temp_c)
	{
		this.temp_c = temp_c;
	}

	public DataItem getHumidity()
	{
		return humidity;
	}

	public void setHumidity(final DataItem humidity)
	{
		this.humidity = humidity;
	}

	public DataItem getIcon()
	{
		return icon;
	}

	public void setIcon(final DataItem icon)
	{
		this.icon = icon;
	}

	public DataItem getWind_direction()
	{
		return wind_direction;
	}

	public void setWind_direction(final DataItem wind_direction)
	{
		this.wind_direction = wind_direction;
	}
}

@XmlAccessorType(XmlAccessType.FIELD)
class DataItem
{

	@XmlAttribute(required = true)
	protected String data;

	public String getData()
	{
		return data;
	}

	public void setData(final String value)
	{
		this.data = value;
	}

	@Override
	public String toString()
	{
		return data;
	}
}

@XmlRootElement(name="forecast_information")
class ForecastInformation
{
	private DataItem city;

	public DataItem getCity()
	{
		return city;
	}

	public void setCity(final DataItem city)
	{
		this.city = city;
	}
}

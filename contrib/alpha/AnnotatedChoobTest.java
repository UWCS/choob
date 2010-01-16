import uk.co.uwcs.choob.ChoobCommand;
import uk.co.uwcs.choob.ChoobParam;
import uk.co.uwcs.choob.ChoobPlugin;
import uk.co.uwcs.choob.ChoobUtil;
import uk.co.uwcs.choob.support.events.Message;
import uk.co.uwcs.choob.modules.Modules;
import uk.co.uwcs.choob.support.IRCInterface;

@ChoobPlugin
(
	description="A plugin to demonstrate the use of annotations to create a choob plugin.",
	author="Benjamin Weber",
	email="benji@benjiweber.co.uk",
	date="date",
	revision="test"
)
public class AnnotatedChoobTest
{
	@ChoobUtil
	private Modules mods;
	@ChoobUtil
	private IRCInterface irc;

	@ChoobCommand (name="greet",help="This command says hello world.")
	public String simpleExample(@ChoobParam(name="Name",description="The name of the user to greet") String name)
	{
		return "Hello World " + name;
	}
	
	@ChoobCommand
	(
		name="greet",
		help="Slaps the specified user"
	)
	public void anotherExample
		(
			@ChoobParam(name="Name", description="The user to slap.") String name,
			Message mes
		)
	{
		irc.sendContextAction(mes,"slaps " + name);
	}

}
										
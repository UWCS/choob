/*
 * Context.java
 *
 * Created on June 16, 2005, 3:02 PM
 */

package org.uwcs.choob;

/**
 *
 * @author  sadiq
 */
public class Context
{
    /**
     * Holds value of property millis.
     */
    private long millis;
    
    /**
     * Holds value of property nick.
     */
    private String nick;
    
    /**
     * Holds value of property channel.
     */
    private String channel;
    
    /**
     * Holds value of property privMessage.
     */
    private boolean privMessage;
    
    /**
     * Holds value of property random.
     */
    private int random;
    
    /**
     * Holds value of property text.
     */
    private String text;
    
    /**
     * Holds value of property bot.
     */
    private Choob bot;
    
    public Context(String nick, String channel, String text, boolean privMessage, Choob bot)
    {
        this.nick = nick;
        this.channel = channel;
        this.text = text;
        this.privMessage = privMessage;
        this.bot = bot;
        this.random = ((int)(Math.random()*127));
        this.millis = System.currentTimeMillis();
    }
    
    /**
     * Getter for property millis.
     * @return Value of property millis.
     */
    public long getMillis()
    {
        return this.millis;
    }
    
    /**
     * Setter for property millis.
     * @param millis New value of property millis.
     */
    public void setMillis(long millis)
    {
        this.millis = millis;
    }
    
    /**
     * Getter for property nick.
     * @return Value of property nick.
     */
    public String getNick()
    {
        return this.nick;
    }
    
    /**
     * Setter for property nick.
     * @param nick New value of property nick.
     */
    public void setNick(String nick)
    {
        this.nick = nick;
    }
    
    /**
     * Getter for property channel.
     * @return Value of property channel.
     */
    public String getChannel()
    {
        return this.channel;
    }
    
    /**
     * Setter for property channel.
     * @param channel New value of property channel.
     */
    public void setChannel(String channel)
    {
        this.channel = channel;
    }
    
    /**
     * Getter for property privMessage.
     * @return Value of property privMessage.
     */
    public boolean isPrivMessage()
    {
        return this.privMessage;
    }
    
    /**
     * Setter for property privMessage.
     * @param privMessage New value of property privMessage.
     */
    public void setPrivMessage(boolean privMessage)
    {
        this.privMessage = privMessage;
    }
    
    /**
     * Getter for property random.
     * @return Value of property random.
     */
    public int getRandom()
    {
        return this.random;
    }
    
    /**
     * Setter for property random.
     * @param random New value of property random.
     */
    public void setRandom(int random)
    {
        this.random = random;
    }
    
    /**
     * Getter for property text.
     * @return Value of property text.
     */
    public String getText()
    {
        return this.text;
    }
    
    /**
     * Setter for property text.
     * @param text New value of property text.
     */
    public void setText(String text)
    {
        this.text = text;
    }
    
    public void sendMessage(String message)
    {
        if( privMessage )
        {
            bot.sendMessage(nick,message);
        }
        else
        {
            bot.sendMessage(channel,message);
        }
    }
}

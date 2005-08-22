package org.uwcs.choob.modules;

public class StoreObject
{
    public int id;
    public String name;
    public String position;
    
    public StoreObject()
    {
        super();
    }
    
        /*public StoreObject( int id )
        {
            this.id = id;
        }*/
    
    /**
     * Getter for property name.
     * @return Value of property name.
     */
    public java.lang.String getName()
    {
        return name;
    }
    
    /**
     * Setter for property name.
     * @param name New value of property name.
     */
    public void setName(java.lang.String name)
    {
        this.name = name;
    }
    
    /**
     * Getter for property position.
     * @return Value of property position.
     */
    public java.lang.String getPosition()
    {
        return position;
    }
    
    /**
     * Setter for property position.
     * @param position New value of property position.
     */
    public void setPosition(java.lang.String position)
    {
        this.position = position;
    }
    
    public String toString()
    {
        return "(name: " + name + ", position: " + position + ")";
    }
    
    /**
     * Getter for property id.
     * @return Value of property id.
     */
    public int getID()
    {
        return id;
    }
    
    /**
     * Setter for property id.
     * @param id New value of property id.
     */
    public void setID(int id)
    {
        this.id = id;
    }
    
}
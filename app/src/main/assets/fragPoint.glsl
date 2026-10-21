precision highp float;
uniform float u_alpha;
uniform float u_mode;
uniform sampler2D u_textureNumber;

void main()
{
    if(u_mode==0.0)
    {
     gl_FragColor = texture2D(u_textureNumber, gl_PointCoord);
    }
    else if(u_mode==1.0)
    {
     gl_FragColor = vec4(0,0,0,u_alpha);
    }
    else
    {
     gl_FragColor = vec4(0.85,0.85,0.85,u_alpha);
    }
}
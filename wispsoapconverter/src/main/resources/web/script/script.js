//import React from "./libs/react.js";
//import ReactDOM from "./libs/react-dom.js";
//import htm from "./libs/htm.module.js";
const html = htm.bind(React.createElement);

//import Menu from './menu.js'
//import Changelogs from "./changelogs.js";
//import Configuration from "./configuration.js";
//import Actions from "./actions.js";
//import Jobs from "./jobs.js";

const fetchOpts = {cache: "reload"}

window.baseUrl = `${document.location.origin + document.location.pathname}`
window.restInputUrl = `${document.location.origin + document.location.pathname.replaceAll(/\/monitor$/g,"")}`
if(document.location.host==='localhost:5500'){
  window.baseUrl = `http://localhost:8080/monitor`
  window.restInputUrl = `http://localhost:8080`  
}

function notify(mex,title){
    window.createNotification()({message:mex,title:title})
}
function notifyErr(mex,title){
    window.createNotification({theme:'error'})({message:mex,title:title})
}


function Router(props) {

    const [hash, setHash] = React.useState(location.hash.replace('#/',''));
    const [version, setVersion] = React.useState();
    const [buildTime, setBuildTime] = React.useState();
    const [instance, setInstance] = React.useState("");
    const [identifier, setIdentifier] = React.useState("");
    const [loading, setLoading] = React.useState(true);

    React.useEffect(() => {
        // Update the document title using the browser API
        window.addEventListener('hashchange',e=>{
            setHash(location.hash.replace('#/',''))
        })
        fetch(`${baseUrl}/version`)
            .then(data=>data.json())
            .then(build=>{
                setVersion(build.version)
                setBuildTime(new Date(build.buildTime).toLocaleString('it-IT',{"day":	"2-digit",month:"2-digit",year:"2-digit",hour:"2-digit",minute:"2-digit",second:"2-digit"}))
                setInstance(build.instance || '')
                setIdentifier(build.identifier || 'not set')
                document.title = `Monitor ${build.instance || ''}`
            })
      },[]);

    return html`<${React.Fragment}>
    <${Menu} page=${hash} version=${version} buildTime=${buildTime} instance=${instance} identifier=${identifier}/>
    <section id="container" className="monospace ${loading?'loading':''}">
        ${loading && html`<div className="loader">
            <svg xmlns="http://www.w3.org/2000/svg" className="icon icon-tabler icon-tabler-loader" width="44" height="44" viewBox="0 0 24 24" strokeWidth="1" stroke="#2c3e50" fill="none" strokeLinecap="round" strokeLinejoin="round">
              <path stroke="none" d="M0 0h24v24H0z" fill="none"/>
              <line x1="12" y1="6" x2="12" y2="3" />
              <line x1="16.25" y1="7.75" x2="18.4" y2="5.6" />
              <line x1="18" y1="12" x2="21" y2="12" />
              <line x1="16.25" y1="16.25" x2="18.4" y2="18.4" />
              <line x1="12" y1="18" x2="12" y2="21" />
              <line x1="7.75" y1="16.25" x2="5.6" y2="18.4" />
              <line x1="6" y1="12" x2="3" y2="12" />
              <line x1="7.75" y1="7.75" x2="5.6" y2="5.6" />
            </svg>
        </div>`}
      ${hash==''?html`<${Jobs} setLoading=${setLoading}/>`:''}
      ${hash=='changelogs'?html`<${Changelogs} setLoading=${setLoading}/>`:''}
      ${hash=='configuration'?html`<${Configuration} setLoading=${setLoading}/>`:''}
      ${hash=='rendicontazioni'?html`<${RendiBollo} setLoading=${setLoading}/>`:''}
      
      ${hash=='actions'?html`<${Actions} setLoading=${setLoading}/>`:''}
    </section>
    </${React.Fragment}>`;
  }


  const App = (props) => {
    return html`<${Router}/>`;
  };


  ReactDOM.render(
    html`<${App} foo=${"bar"} />`,
    document.getElementById("root")
  );
//import React from "./libs/react.js";
//import htm from "./libs/htm.module.js";
//const html = htm.bind(React.createElement);

//import ActionButton from './actionButton.js'
function Actions(props) {

    const [actions, setActions] = React.useState([]);
    const [jobs, setJobs] = React.useState([]);

    React.useEffect(()=>{
        props.setLoading(true)
        Promise.all([
            fetch(`${baseUrl}/keys`)
            .then(data=>data.json()),
            fetch(`${baseUrl}/jobs`)
            .then(data=>data.json())
        ])
        .then(cl => {
            setActions(cl[0])
            setJobs(cl[1])
            props.setLoading(false)
        })
    },[])

    return html`<div className="gridActions" >
    <div><span className="bold">Refresh Configuration</span>
    ${
        actions.map(refKey=>{
            return html`<${ActionButton} key=${refKey.refreshableKey} url=${restInputUrl}/config/refresh/${refKey.refreshableKey} text=${refKey.refreshableKeyDescription}/>`
        })
    }
    </div>
    <div><span className="bold">Trigger job</span>
    ${
        jobs.map(job=>{
            return html`<${ActionButton} key=${job.name} url=${restInputUrl}/jobs/trigger/${job.name} text=${job.descr}/>`
        })
    }
    </div>
    <div><span className="bold">Other</span>
        <${ActionButton} url=${baseUrl}/resetRunningJob text="Reset running jobs"/>
        <${ActionButton} url=${baseUrl}/health text="Cluster status"/>
    </div>
</div>`;
  }

//export default Actions